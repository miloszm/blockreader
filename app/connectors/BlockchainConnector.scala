package connectors

import java.time.{LocalDateTime, ZoneId}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import model.{BlockConnectorError, BlockReaderError, JsonBlock, JsonBlocks}
import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait BlockchainConnector {

  implicit val formatBlock = Json.format[JsonBlock]
  implicit val formatBlocks = Json.format[JsonBlocks]

  def toEpochMilli(localDateTime: LocalDateTime) =
     localDateTime.atZone(ZoneId.systemDefault())
      .toInstant.toEpochMilli

  def getBlocks(): Future[Validated[BlockReaderError, JsonBlocks]] = {
    val request = WS.url(s"https://blockchain.info/blocks/${toEpochMilli(LocalDateTime.now)}?format=json")
    val futureResponse = request.get

    futureResponse.map { response =>
      Valid[JsonBlocks](response.json.validate[JsonBlocks].get)
    }.recover {
      case e: Exception =>
        Invalid[BlockReaderError](BlockConnectorError(1, e.getMessage))
    }
  }

}

object BlockchainConnector extends BlockchainConnector
