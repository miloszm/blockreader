package controllers

import javax.inject._

import akka.actor.ActorSystem
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import play.api.mvc._
import views.html.{blocks_template, transactions_template}

import scala.concurrent.ExecutionContext

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
    val futureValBlock = BlockchainConnector.getBlock("000000000000000000318df689850b6fe75cbad28d08540d319229e83df28000")
    futureValBlock.map{
      case Valid(jsonBlock) => Ok(transactions_template("", jsonBlock.toBlock))
      case Invalid(error) => Ok(error.message)
    }
  }

}
