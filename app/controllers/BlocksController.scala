package controllers

import javax.inject._

import akka.actor.ActorSystem
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import model._
import play.api.mvc._
import views.html.{blocks_template, rich_blocks_template, transactions_template}

import scala.concurrent.{ExecutionContext, Future}

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
//    val futureValBlock = BlockchainConnector.getBlock("000000000000000000318df689850b6fe75cbad28d08540d319229e83df28000")
    val futureValBlock = BlockchainConnector.getBlock("000000000000000000600f97b9bbe3dfbf76967a5a49b0595ebb6b750e96df15")
    futureValBlock.map{
      case Valid(jsonBlock) => Ok(transactions_template("", jsonBlock.toBlock))
      case Invalid(error) => Ok(error.message)
    }
  }

  def richBlocks: Action[AnyContent] = Action.async {
    val futureValRichBlocks = BlockchainConnector.getBlocks()
    val futSeqValidated = futureValRichBlocks.flatMap { x =>
      enrichBlocks(x)
    }
    futSeqValidated.map { seqValidated =>
      val valid = seqValidated.collect { case Valid(aaa) => aaa }
      valid match {
        case Nil => Ok("empty")
        case l => Ok(rich_blocks_template("", RichBlocks(l)))
      }
    }
  }

  def enrichBlocks(blocks: Validated[BlockReaderError, JsonBlocks]): Future[Seq[Validated[BlockReaderError, RichBlockEntry]]] = {
    blocks match {
      case Valid(bl) => {
        val richBlockEntries = for {
          jsonBlockEntry <- bl.blocks
        } yield {
          BlockchainConnector.getBlock(jsonBlockEntry.hash) map {
            case Valid(jb) => Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock))
            case Invalid(e) => Invalid(e)
          }
        }
        Future.sequence(richBlockEntries)
      }
      case Invalid(e) => Future.successful(Seq(Invalid(e)))
    }
  }

}
