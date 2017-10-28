package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import model._
import play.api.Logger
import play.api.mvc._
import views.html.{blocks_template, rich_blocks_template, transactions_template}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BlocksController @Inject()(actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends Controller {

  implicit val system = ActorSystem("blockreader")
  implicit val materializer = ActorMaterializer()

  val logger = Logger

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

  def enrichBlocks2(blocks: Validated[BlockReaderError, JsonBlocks]): Future[Seq[Validated[BlockReaderError, RichBlockEntry]]] = {
    blocks match {
      case Valid(bl) => {
        val richBlockEntries = for {
          jsonBlockEntry <- bl.blocks
        } yield {
          val response = BlockchainConnector.getBlock(jsonBlockEntry.hash)
          response map {
            case Valid(jb) =>
              logger.info(s"valid block entry ${jsonBlockEntry.height}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock))
            case Invalid(e) =>
              logger.info(s"invalid block entry ${jsonBlockEntry.height}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, EmptyBlock))
          }
        }
        val futureRichEntries = Future.sequence(richBlockEntries)
        logger.info(s"sequence of ${richBlockEntries.size} block requests")
        futureRichEntries
      }
      case Invalid(e) => Future.successful(Seq(Invalid(e)))
    }
  }

  def enrichBlocks(blocks: Validated[BlockReaderError, JsonBlocks]): Future[Seq[Validated[BlockReaderError, RichBlockEntry]]] = {
    blocks match {
      case Valid(bl) => {
        val source: Source[JsonBlockEntry, NotUsed] = Source.apply[JsonBlockEntry](bl.blocks.toList)
        val s = source.throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 5, ThrottleMode.Shaping).map{ jsonBlockEntry =>
          val response = BlockchainConnector.getBlock(jsonBlockEntry.hash)
          response map {
            case Valid(jb) =>
              logger.info(s"valid block entry ${jsonBlockEntry.height}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock))
            case Invalid(e) =>
              logger.info(s"invalid block entry ${jsonBlockEntry.height}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, EmptyBlock))
          }
        }

        val richBlockEntries = s.runFold(List[Future[Validated.Valid[RichBlockEntry]]]())((l,el) => l :+ el)

        val futureRichEntries = richBlockEntries.flatMap(Future.sequence(_))
        logger.info(s"sequence of ${bl.blocks.size} block requests")
        futureRichEntries
      }
      case Invalid(e) => Future.successful(Seq(Invalid(e)))
    }
  }

}
