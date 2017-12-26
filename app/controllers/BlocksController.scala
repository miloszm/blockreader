package controllers

import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.{AkkaHttpClient, BlockchainConnector}
import model._
import play.api.Logger
import play.api.cache.CacheApi
import play.api.mvc._
import views.html.{blocks_template, rich_blocks_template, transactions_template}

import scala.collection.immutable.Seq
import scala.concurrent.duration.{Duration, FiniteDuration, MINUTES}
import scala.concurrent.{ExecutionContext, Future, duration}

@Singleton
class BlocksController @Inject()(actorSystem: ActorSystem, cache: CacheApi)(implicit exec: ExecutionContext) extends Controller {

  implicit val system = ActorSystem("blockreader")
  implicit val materializer = ActorMaterializer()

  val blockchainConnector = BlockchainConnector(cache, AkkaHttpClient)
  val logger = Logger
  val count = new AtomicLong(0L)
  def counter = count.incrementAndGet()

  def blocks: Action[AnyContent] = Action.async {
    val futureValBlocks = blockchainConnector.getBlocks
    futureValBlocks.map {
      case Valid(jsonBlocks) => Ok(blocks_template("", jsonBlocks.toBlocks))
      case Invalid(error) => Ok(error.message)
    }
  }

  def genesis(): Action[AnyContent] =
    block("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f")

  def block(hash: String): Action[AnyContent] = Action.async {
    val futureValBlock = blockchainConnector.getBlock(hash, 0)
    futureValBlock.map {
      case Valid(jsonBlock) => Ok(transactions_template("", jsonBlock.toBlock))
      case Invalid(error) => Ok(error.message)
    }
  }

  def richBlocks: Action[AnyContent] = Action.async {
    val futureValRichBlocks = blockchainConnector.getBlocks
    val futSeqValidated = futureValRichBlocks.flatMap( enrichBlocks )
    futSeqValidated.map { seqValidated =>
      val valid = seqValidated.collect { case Valid(richBlockEntry) => richBlockEntry }
      valid match {
        case Nil => Ok("empty")
        case l => Ok(rich_blocks_template("", RichBlocks(l), counter))
      }
    }
  }

  def enrichBlocks(blocks: Validated[BlockReaderError, JsonBlocks]): Future[Seq[Validated[BlockReaderError, RichBlockEntry]]] = {
    blocks match {
      case Valid(bl) =>
        produceRichBlockEntries(bl)
      case Invalid(e) =>Future.successful(Seq(Invalid(e)))
    }
  }

  private def produceRichBlockEntries(bl: JsonBlocks): Future[Seq[Valid[RichBlockEntry]]] = {
    val source = Source.apply[JsonBlockEntry](bl.blocks.toList)
      .throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.Shaping)

    val richBlockEntrySource = source.mapAsync(parallelism = 10) { jsonBlockEntry =>
      val response = blockchainConnector.getBlock(jsonBlockEntry.hash, jsonBlockEntry.height)
      response map {
        case Valid(jb) =>
          cache.getOrElse(jsonBlockEntry.height.toString) {
            jb
          }
          Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock))
        case Invalid(e) =>
          logger.info(s"invalid block entry ${jsonBlockEntry.height} ${e.message}")
          cache.remove(jsonBlockEntry.height.toString)
          Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, EmptyBlock))
      }
    }

    val richBlockEntries: Future[Seq[Valid[RichBlockEntry]]] =
      cache.getOrElse(bl.signature, Duration(20, MINUTES)) {
        richBlockEntrySource.runWith(Sink.seq)
      }
    logger.info(s"sequence of ${bl.blocks.size} block requests")
    richBlockEntries
  }
}
