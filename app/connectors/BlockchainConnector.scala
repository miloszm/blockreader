package connectors

import java.time.{LocalDate, LocalDateTime, ZoneId}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector.toEpochMilli
import model.{BlockReaderError, _}
import play.api.Logger
import play.api.Play.current
import play.api.cache.CacheApi
import play.api.libs.json.Json
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

case class BlockchainConnector(cache: CacheApi, httpClient: HttpClient) {

  implicit val system: ActorSystem = ActorSystem("blockreader")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val logger = Logger

  implicit val formatOutput = Json.format[JsonOutput]
  implicit val formatInput = Json.format[JsonInput]
  implicit val formatTransaction = Json.format[JsonTransaction]
  implicit val formatBlock = Json.format[JsonBlock]
  implicit val formatBlockEntry = Json.format[JsonBlockEntry]
  implicit val formatBlocks = Json.format[JsonBlocks]
  implicit val formatLatestBlock = Json.format[LatestBlock]
  implicit val formatUsdPrice = Json.format[UsdPrice]
  implicit val formatPriceTicker = Json.format[PriceTicker]

  def getLatestBlock: Future[Int] = {
    val request = WS.url(s"https://blockchain.info/latestblock")
    val futureResponse = request.get
    futureResponse.map { response =>
      val latestBlock = response.json.validate[LatestBlock].get
      logger.info(s"latest block is ${latestBlock.height}")
      Valid[LatestBlock](latestBlock)
    }.map(x => x.map(_.height).getOrElse(-1))
  }

  def getUsdPrice: Future[BigDecimal] = {
    val request = WS.url(s"https://blockchain.info/ticker")
    val futureResponse = request.get
    futureResponse.map { response =>
      val priceTicker = response.json.validate[PriceTicker].get
      Valid[PriceTicker](priceTicker)
    }.map(x => x.map(_.`USD`.`15m`).map(_.setScale(2, RoundingMode.FLOOR)).getOrElse(0))
  }

  def getBlocks: Future[Validated[BlockReaderError, JsonBlocks]] = {
    getLatestBlock.flatMap { latestBlock =>
      val currentFeeResult = cache.get("feeresult").getOrElse(FeeResult.empty)
      if (currentFeeResult.topBlock == latestBlock && !currentFeeResult.emptyBlocksExist){
        Future.successful(Valid(JsonBlocks(Nil)))
      }
      else {
        val d1 = doGetBlocks(LocalDateTime.now)
        val d2 = doGetBlocks(LocalDate.now.atStartOfDay().minusSeconds(2))
        val futValBlocks = for {
          v1 <- d1
          v2 <- d2
        } yield v1.combine(v2)(BlockReaderError, JsonBlocks)
        futValBlocks.map { valBlocks =>
          valBlocks.map { blocks =>
            val sortedBlocks = JsonBlocks(blocks.blocks.sortWith((a, b) => a.time >= b.time))
            sortedBlocks
          }
        }
      }
    }
  }

  def doGetBlocks(dateTime:LocalDateTime): Future[Validated[BlockReaderError, JsonBlocks]] = {
    val futureResponse = httpClient.get(s"https://blockchain.info/blocks/${toEpochMilli(dateTime)}?format=json")
    futureResponse.flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          val jsonResult = Unmarshal(response.entity).to[String]
          jsonResult.map {string =>
            Json.parse(string)
              .validate[JsonBlocks]
              .fold(e => { Invalid[BlockReaderError](BlockReaderError(1, e.toString))},
                jsonBlocks => {
                  Valid[JsonBlocks](jsonBlocks)
                }
              )
          }.recover {
            case e: Exception =>
              logger.info(s"exception in getBlocks - ${e.getMessage}")
              Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
          }
        case status =>
          logger.info(s"failure in getBlocks for $dateTime - ${status.toString}")
          Future.successful(Invalid[BlockReaderError](BlockReaderError(1, status.toString())))
      }
    }.recover {
      case e: Exception =>
        logger.info(s"exception in getBlocks - ${e.getMessage}")
        Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
    }
  }

  def getBlock(blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
    doGetBlock(blockHash, blockHeight)
  }

  def doGetBlock(blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
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

}


object BlockchainConnector {
  def toEpochMilli(localDateTime: LocalDateTime) =
     localDateTime.atZone(ZoneId.systemDefault())
      .toInstant.toEpochMilli
}
