package connectors

import java.time.{LocalDate, LocalDateTime, ZoneId}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector.toEpochMilli
import model._
import play.api.Logger
import play.api.Play.current
import play.api.cache.CacheApi
import play.api.libs.json.Json
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, HOURS}
import model.BlockReaderError

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

  def getLatestBlock: Future[Int] = {
    val request = WS.url(s"https://blockchain.info/latestblock")
    val futureResponse = request.get
    futureResponse.map { response =>
      val latestBlock = response.json.validate[LatestBlock].get
      logger.info(s"latest block is ${latestBlock.height}")
      Valid[LatestBlock](latestBlock)
    }.map(x => x.map(_.height).getOrElse(-1))
  }

  def getBlocks: Future[Validated[BlockReaderError, JsonBlocks]] = {
    getLatestBlock.flatMap { latestBlock =>
      cache.get[JsonBlocks](s"blocks${latestBlock.toString}") match {
        case Some(blocks) =>
          logger.info(s"blocks summary from cache for ${blocks.signature}")
          Future.successful(Valid(blocks))
        case _ => {
          val d1 = doGetBlocks(LocalDateTime.now)
          val d2 = doGetBlocks(LocalDate.now.atStartOfDay().minusSeconds(2))
          val futValBlocks = for {
            v1 <- d1
            v2 <- d2
          } yield v1.combine(v2)(BlockReaderError, JsonBlocks)
          futValBlocks.map { valBlocks =>
            valBlocks.map{ blocks =>
              val sortedBlocks = JsonBlocks(blocks.blocks.sortWith((a,b) => a.time >= b.time))
              cache.set(s"blocks${blocks.height.toString}", sortedBlocks, Duration(49, HOURS))
              sortedBlocks
            }
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
          logger.info(s"failure in getBlocks - status.toString")
          Future.successful(Invalid[BlockReaderError](BlockReaderError(1, status.toString())))
      }
    }.recover {
      case e: Exception =>
        logger.info(s"exception in getBlocks - ${e.getMessage}")
        Invalid[BlockReaderError](BlockReaderError(1, e.getMessage))
    }
  }

  def getBlock(blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
    cache.get[JsonBlock](blockHeight.toString) match {
      case Some(block) =>
        //logger.info(s"cached: ${blockHeight.toString}")
        Future.successful(Valid(block))
      case _ =>
        doGetBlock(blockHash, blockHeight)
    }
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
                  cache.set(blockHeight.toString, r, Duration(25, HOURS))
                  logger.info(s"added to cache ${blockHeight.toString}")
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
