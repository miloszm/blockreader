package connectors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer

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
