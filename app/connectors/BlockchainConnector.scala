package connectors

import java.time.{LocalDate, LocalDateTime, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector.{CutOffHours, CutOffMinutes, toEpochMilli}
import model.json._
import model.{BlockReaderError, _}
import play.api.Logger

import scala.util.{Failure, Success, Try}
//import play.api.Play.current
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
//import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode


object Formats {
  implicit val formatOutput = Json.format[JsonOutput]
  implicit val formatInput = Json.format[JsonInput]
  implicit val formatTransaction = Json.format[JsonTransaction]
  implicit val formatBlock = Json.format[JsonBlock]
  implicit val formatBlockEntry = Json.format[JsonBlockEntry]
  implicit val formatBlocks = Json.format[JsonBlocks]
  implicit val formatLatestBlock = Json.format[JsonLatestBlock]
  implicit val formatUsdPrice = Json.format[JsonUsdPrice]
  implicit val formatPriceTicker = Json.format[JsonPriceTicker]
}

@Singleton
case class BlockchainConnector @Inject()(cache: SyncCacheApi, httpClient: HttpClient, btcConn: BitcoinSConnector, blockApi: BitcoinSBlockFutureApi) {
  import Formats._

  val counter = new AtomicLong(0)

  implicit val system: ActorSystem = ActorSystem("blockreader")
  //implicit val materializer: ActorMaterializer = ActorMaterializer()
  val logger = Logger

  def getLatestBlock: Future[Int] = {
    btcConn.getLatestBlock.map { latestBlock =>
      //logger.info(s"BlockchainConnector: latest block from BitcoinSConnector is $latestBlock")
      latestBlock
    }
  }

  def getUsdPrice(implicit mat: Materializer): Future[BigDecimal] = {
    val request = httpClient.get(s"https://blockchain.info/ticker")
    val futureResponse = request
    futureResponse.flatMap { response =>
      val jsonResponse = Unmarshal(response.entity).to[String]
      jsonResponse.map{string =>
        Try { Json.parse(string).validate[JsonPriceTicker].asOpt }.fold(
          t => {
            logger.info(s"exception caught when getting/converting price: ${t.getMessage}", t)
            Some(JsonPriceTicker(JsonUsdPrice(0)))
          }, identity
        )
      }
    }.map(x => x.map(_.`USD`.`15m`).map(_.setScale(2, RoundingMode.FLOOR)).getOrElse(0))
  }

  def getBlocks(latestBlock: Int)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlocks]] = {
      val currentFeeResult = cache.get("feeresult").getOrElse(FeeResult.empty)
      if (currentFeeResult.topBlock == latestBlock && !currentFeeResult.emptyBlocksExist){
        logger.info(s"BlockchainConnector: getBlocks returning nil as ${currentFeeResult.topBlock} == $latestBlock - ${counter.getAndAdd(20)}")
        Future.successful(Valid(JsonBlocks(Nil)))
      }
      else {
        logger.info(s"BlockchainConnector: getting blocks")
        val momentNow = LocalDateTime.now
        val cutOffTime = momentNow.minusHours(CutOffHours).minusMinutes(CutOffMinutes)
        val cutOffTimeEpoch = cutOffTime.atOffset(ZoneOffset.UTC).toEpochSecond
        val d1 = doGetBlocks(momentNow)
        val d2 = doGetBlocks(LocalDate.now.atStartOfDay().minusSeconds(2))
        val futValBlocks = for {
          v1 <- d1
          v2 <- d2
        } yield v1.combine(v2)(BlockReaderError, JsonBlocks)
        futValBlocks.map { valBlocks =>
          valBlocks.map { jsonBlocks =>
            val sortedBlocks = JsonBlocks(jsonBlocks.blocks.sortWith((a, b) => a.time >= b.time))
            logger.info(s"BlockchainConnector: getting blocks - blocks before cutoff filter ${sortedBlocks.blocks.size}")
            val filteredBlocks = JsonBlocks(sortedBlocks.blocks.filter(_.time >= cutOffTimeEpoch))
            logger.info(s"BlockchainConnector: getting blocks - blocks after cutoff filter ${filteredBlocks.blocks.size} momentNow=${momentNow.toEpochSecond(ZoneOffset.UTC)} cutOff=$cutOffTimeEpoch")
            filteredBlocks
          }
        }
      }
  }

  def doGetBlocks(dateTime:LocalDateTime)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlocks]] = {
    // https://api.blockcypher.com/v1/btc/main/blocks/294322?txstart=1&limit=1 // use blockcypher as blockchain.info/blocks stopped working
    // dont need to convert block height to hashes as blockcypher has API based on height so we can skip this step
    // also, we can assume 24*6 blocks per day plus some extra, and then filter by time
    // we get latest height, go back 200 blocks, and then enrich only those which are within 24h
    val url = s"https://blockchain.info/blocks/${toEpochMilli(dateTime)}?format=json"
    logger.info(s"BlockchainConnector: doGetBlocks getting $url")
    val futureResponse = httpClient.get(url)
    futureResponse.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          logger.info(s"BlockchainConnector: doGetBlocks status is 200")
          val jsonResult = Unmarshal(response.entity).to[String]
          jsonResult.map {string =>
            Json.parse(string)
              .validate[JsonBlocks]
              .fold(e => { Invalid[BlockReaderError](BlockReaderError(1, e.toString))},
                jsonBlocks => {
                  logger.info(s"BlockchainConnector: doGetBlocks obtained ${jsonBlocks.blocks.size} blocks")
                  Valid[JsonBlocks](jsonBlocks)
                }
              )
          }.recover {
            case e: Exception =>
              logger.info(s"exception in getBlocks - ${e.getMessage}")
              Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
          }
        case status =>
          logger.info(s"failure in getBlocks - ${status.toString}")
          Future.successful(Invalid[BlockReaderError](BlockReaderError(1, status.toString())))
      }
    }.recover {
      case e: Exception =>
        logger.info(s"exception in getBlocks - ${e.getMessage}")
        Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
    }
  }

  def getBlock(blockHash: String, blockHeight: Int, local: Boolean = false)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlock]] = {
    if (local)
      doGetBlockLocal(blockHash, blockHeight)
    else
      doGetBlockRemote(blockHash, blockHeight)
  }

  def doGetBlockRemote(blockHash: String, blockHeight: Int)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlock]] = {
    val futureResponse = httpClient.get(s"https://blockchain.info/rawblock/$blockHash")

    futureResponse.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          val jsonResult = Unmarshal(response.entity).to[String]
          jsonResult.map {string =>
            Json.parse(string)
              .validate[JsonBlock]
              .fold(e => { Invalid[BlockReaderError](BlockReaderError(1, e.toString))},
                r => {
                  Valid[JsonBlock](r)
                }
              )
          }.recover {
            case e: Exception =>
              Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
          }
        case status =>
          Future.successful(Invalid[BlockReaderError](BlockReaderError(1, status.toString())))
      }
    }.recover {
      case e: Exception =>
        Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
    }
  }

  def doGetBlockLocal(blockHash: String, blockHeight: Int)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlock]] = {
    logger.info(s"BlockchainConnector: getting locally block $blockHeight")
    blockApi.getMhmBlockWithClient(btcConn.rpcCli, blockHash, blockHeight)
  }

}


object BlockchainConnector {
  def toEpochMilli(localDateTime: LocalDateTime) =
     localDateTime.atZone(ZoneId.systemDefault())
      .toInstant.toEpochMilli
  val CutOffHours = 1
  val CutOffMinutes = 30
}
