import akka.http.scaladsl.model.HttpResponse
import connectors.{AkkaHttpClient, HttpClient}
import org.scalatest.TestData
import org.scalatestplus.play._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._
import play.api.test.Helpers._
import play.api.inject.bind

import scala.concurrent.Future



object MockHttpClient extends HttpClient {
  override def get(url: String): Future[HttpResponse] = ???
}


/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with OneAppPerTest {

  override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder()
    .overrides(bind(classOf[HttpClient]).toInstance(AkkaHttpClient))
    .build

  "Routes" should {

    "send 404 on a bad request" in  {
      route(app, FakeRequest(GET, "/boum")).map(status(_)) mustBe Some(NOT_FOUND)
    }

  }

  "HomeController" should {

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/all")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      println(contentAsString(home))
      //contentAsString(home) must include ("Your new application is ready.")
    }

  }

  "CountController" should {

    "return an increasing count" in {
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "0"
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "1"
      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "2"
    }

  }

}
