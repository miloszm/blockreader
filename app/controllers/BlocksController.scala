package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import model._
import play.api.Logger
import play.api.mvc._
import views.html.{blocks_template, rich_blocks_template, transactions_template}

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BlocksController @Inject()(actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends Controller {

  implicit val system = ActorSystem("blockreader")
  implicit val materializer = ActorMaterializer()

  val logger = Logger

  def blocks: Action[AnyContent] = Action.async {
    val futureValBlocks = BlockchainConnector.getBlocks()
    futureValBlocks.map {
      case Valid(jsonBlocks) => Ok(blocks_template("", jsonBlocks.toBlocks))
      case Invalid(error) => Ok(error.message)
    }
  }

  def anyblock(): Action[AnyContent] =
    block("000000000000000000318df689850b6fe75cbad28d08540d319229e83df28000")

  def block(hash: String): Action[AnyContent] = Action.async {
    val futureValBlock = BlockchainConnector.getBlock(hash)
    futureValBlock.map {
      case Valid(jsonBlock) => Ok(transactions_template("", jsonBlock.toBlock))
      case Invalid(error) => Ok(error.message)
    }
  }

  def richBlocks: Action[AnyContent] = Action.async {
    val futureValRichBlocks = BlockchainConnector.getBlocks()
    val futSeqValidated = futureValRichBlocks.flatMap( enrichBlocks )
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
        val source = Source.apply[JsonBlockEntry](bl.blocks.toList)
          .throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.Shaping)

        val richBlockEntrySource = source.mapAsync(parallelism = 10) { jsonBlockEntry =>
          val response = BlockchainConnector.getBlock(jsonBlockEntry.hash)
          response map {
            case Valid(jb) =>
              logger.info(s"valid block entry ${jsonBlockEntry.height}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock))
            case Invalid(e) =>
              logger.info(s"invalid block entry ${jsonBlockEntry.height} ${e.message}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, EmptyBlock))
          }
        }

        val richBlockEntries: Future[Seq[Valid[RichBlockEntry]]] = richBlockEntrySource.runWith(Sink.seq)
        logger.info(s"sequence of ${bl.blocks.size} block requests")
        richBlockEntries
      }
      case Invalid(e) => Future.successful(Seq(Invalid(e)))
    }
  }

}
