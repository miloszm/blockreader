package connectors

import java.time.{LocalDateTime, ZoneId}

import model.{JsonBlock, JsonBlocks}
import org.joda.time.{LocalDate, LocalTime}
import play.api.libs.ws.WS

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

trait BlockchainConnector {

  implicit val formatBlock = Json.format[JsonBlock]
  implicit val formatBlocks = Json.format[JsonBlocks]

  def toEpochMilli(localDateTime: LocalDateTime) =
     localDateTime.atZone(ZoneId.systemDefault())
      .toInstant.toEpochMilli

  def getBlocks(): Future[JsonBlocks] = {
    val request = WS.url(s"https://blockchain.info/blocks/${toEpochMilli(LocalDateTime.now)}?format=json")
    request.get.map { response =>
      response.json.validate[JsonBlocks].get
    }
  }

}

object BlockchainConnector extends BlockchainConnector
