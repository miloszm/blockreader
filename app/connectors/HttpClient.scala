package connectors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import model._
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
  override def get(url: String): Future[HttpResponse] = url match {
    case "https://blockchain.info/latestblock" =>
      val body = Json.toJson[LatestBlock](LatestBlock(600000)).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case "https://blockchain.info/ticker" =>
      val body = Json.toJson[PriceTicker](PriceTicker(UsdPrice(BigDecimal(10000)))).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case u if u contains "https://blockchain.info/blocks" =>
      val entries = (0 until 10).map{ i =>
        JsonBlockEntry(600000-i, (600000-i).toString, 1576571445L - i*10*60*1000)
      }
      val body = Json.toJson[JsonBlocks](JsonBlocks(entries)).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
    case u if u contains "https://blockchain.info/rawblock" =>
      val hash = u.replace("https://blockchain.info/rawblock/", "")
      val height = hash.toLong
      val t = JsonTransaction(Nil, Nil, 0L, 0, 0, "abc", 0, 0L)
      val block = JsonBlock(10L, height, 1, Seq(t), 1576571445L - (600000-height)*10*60*1000)
      val body = Json.toJson[JsonBlock](block).toString()
      Future.successful[HttpResponse](HttpResponse.apply(StatusCodes.OK, Nil, HttpEntity(body)))
  }
}