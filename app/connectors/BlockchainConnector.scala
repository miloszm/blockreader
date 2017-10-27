package connectors

import model.{JsonBlock, JsonBlocks}
import play.api.libs.ws.WS

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

trait BlockchainConnector {

  implicit val formatBlock = Json.format[JsonBlock]
  implicit val formatBlocks = Json.format[JsonBlocks]

  def getBlocks(): Future[JsonBlocks] = {
    val request = WS.url("https://blockchain.info/blocks/1509100019000?format=json")
    request.get.map { response =>
      response.json.validate[JsonBlocks].get
    }
  }

}

object BlockchainConnector extends BlockchainConnector
