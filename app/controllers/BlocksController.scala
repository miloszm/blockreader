package controllers

import akka.actor.ActorSystem
import javax.inject._

import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import views.html.blocks_template

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

@Singleton
class BlocksController @Inject()(actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends Controller {

  def blocks: Action[AnyContent] = Action.async {
    val futureValBlocks = BlockchainConnector.getBlocks()
    futureValBlocks.map{
      case Valid(jsonBlocks) => Ok(blocks_template("", jsonBlocks.toBlocks))
      case Invalid(error) => Ok(error.message)
    }
  }

}
