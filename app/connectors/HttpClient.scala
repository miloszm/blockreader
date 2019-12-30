package connectors

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import model._
import model.json._
import play.api.libs.json.Json

import scala.concurrent.Future

trait HttpClient {
  def get(url: String): Future[HttpResponse]
}

object AkkaHttpClient extends HttpClient {
  implicit val system: ActorSystem = ActorSystem("blockreader")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  override def get(url: String): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(uri = url))
}


import Formats._

object MockHttpClient extends HttpClient {
  val numBlocks = 10
  val topHeight = 600000
  val millis24h = 24*3600*1000
  val now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
  override def get(url: String): Future[HttpResponse] = url match {
    case "https://blockchain.info/latestblock" =>
      val body = Json.toJson[JsonLatestBlock](JsonLatestBlock(topHeight)).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case "https://blockchain.info/ticker" =>
      val body = Json.toJson[JsonPriceTicker](JsonPriceTicker(JsonUsdPrice(BigDecimal(10000)))).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case u if u contains "https://blockchain.info/blocks" =>
      val entries = (0 until numBlocks).map{ i =>
        JsonBlockEntry(topHeight-i, (topHeight-i).toString, now - ((millis24h/numBlocks)*i))
      }
      val body = Json.toJson[JsonBlocks](JsonBlocks(entries)).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case u if u contains "https://blockchain.info/rawblock" =>
      val hash = u.replace("https://blockchain.info/rawblock/", "")
      val height = hash.toLong
      val i = topHeight-height
      println(s">>>$hash   >>> i=$i >>> height=$height")
      val tm = now - ((millis24h/numBlocks)*i)
      val transaction = JsonTransaction(Seq(JsonInput(Some(JsonOutput(Some(30000), Some("3ga"), Some("91"))))), Seq(JsonOutput(Some(26000), Some("3ga"), Some("91"))), 0L, 0, 0, "abc", 220, tm/1000)
      val block = JsonBlock(10L, height, 1, Seq(transaction), tm/1000)
      val body = Json.toJson[JsonBlock](block).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
  }
}