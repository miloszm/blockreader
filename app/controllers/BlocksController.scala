package controllers

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.{AkkaHttpClient, BlockchainConnector}
import model._
import play.api.Logger
import play.api.cache.CacheApi
import play.api.mvc._
import views.html._

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

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

  def all: Action[AnyContent] = Action.async {
    val feeResult = cache.getOrElse[FeeResult]("feeresult")(FeeResult.empty)
    Future.successful(Ok(all_template(feeResult)))
  }

  def richBlocks: Action[AnyContent] = Action.async {
    val futureUsdPrice = blockchainConnector.getUsdPrice
    futureUsdPrice.flatMap { usdPrice => {
      val futureValRichBlocks = blockchainConnector.getBlocks
      val futSeqValidated = futureValRichBlocks.flatMap(enrichBlocks)
      futSeqValidated.map { seqValidated =>
        val valid = seqValidated.collect { case Valid(richBlockEntry) => richBlockEntry }
        valid match {
          case Nil => Ok(rich_blocks_empty_template(""))
          case l => {
            val all = l.flatMap(_.block.tx)
            cache.set("feeresult", FeeResult.fromTransactions(AllTransactions(all), l.exists(_.block.isEmpty), usdPrice))
            Ok(rich_blocks_template("", RichBlocks(l), counter))
          }
        }
      }
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
      cache.get[RichBlockEntry](jsonBlockEntry.height.toString) match {
        case Some(richBlockEntry) =>
          logger.info(s"got from cache block ${jsonBlockEntry.height}")
          Future.successful(Valid(richBlockEntry))
        case _ => {
          val response = blockchainConnector.getBlock(jsonBlockEntry.hash, jsonBlockEntry.height)
          response map {
            case Valid(jb) =>
              val rbe = RichBlockEntry(jsonBlockEntry.toBlockEntry, jb.toBlock)
              cache.set(jsonBlockEntry.height.toString, rbe)
              logger.info(s"added to cache block ${jsonBlockEntry.height}")
              Valid(rbe)
            case Invalid(e) =>
              logger.info(s"invalid block entry ${jsonBlockEntry.height} ${e.message}")
              Valid(RichBlockEntry(jsonBlockEntry.toBlockEntry, EmptyBlock))
          }
        }
      }
    }

    val richBlockEntries: Future[Seq[Valid[RichBlockEntry]]] =
      richBlockEntrySource.runWith(Sink.seq)
    if (bl.blocks.nonEmpty) {
      logger.info(s"sequence of ${bl.blocks.size} block requests")
    }
    richBlockEntries
  }
}
