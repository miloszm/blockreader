package controllers

import akka.actor.ActorSystem
import javax.inject._

import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import views.html.{block_template, blocks_template}

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

  def block: Action[AnyContent] = Action.async {
    val futureValBlock = BlockchainConnector.getBlock("0000000000000000004d62df5dc523a661f5b65899b51af9648919972e201c5c")
    futureValBlock.map{
      case Valid(jsonBlock) => Ok(block_template("", jsonBlock.toBlock))
      case Invalid(error) => Ok(error.message)
    }
  }

}
