package controllers

import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import javax.inject._
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, ThrottleMode}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import connectors.BlockchainConnector
import model._
import model.domain.{EmptyBlock, RichBlock, Transactions}
import model.json.{JsonBlockEntry, JsonBlocks}
import org.joda.time.LocalTime
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.mvc._
import views.html._

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class BlocksController @Inject()(actorSystem: ActorSystem, cache: SyncCacheApi, blockchainConnector: BlockchainConnector)(implicit exec: ExecutionContext) extends Controller {

  implicit val system = ActorSystem("blockreader")
  implicit val materializer = ActorMaterializer()

//  val blockchainConnector = BlockchainConnector(cache, AkkaHttpClient)
  val logger = Logger
  val count = new AtomicLong(0L)
  def counter = count.incrementAndGet()

  def blocks: Action[AnyContent] = Action.async {
    val futureValBlocks = blockchainConnector.getBlocks
    futureValBlocks.map {
      case Valid(jsonBlocks) => Ok(blocks_template("", jsonBlocks.toBlockIds))
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
    val feeResult = cache.get[FeeResult]("feeresult").getOrElse(FeeResult.empty)
    Future.successful(Ok(all_template(feeResult)))
  }

  def fees: Action[AnyContent] = Action.async {
    val feeResult = cache.get[FeeResult]("feeresult").getOrElse(FeeResult.empty)
//    val feeResult = FeeResult.fake
    Future.successful(Ok(fees_template(feeResult)))
  }

//  def richBlocks: Action[AnyContent] = Action.async {
//    fetchBlocksUpdateFeeResultInCache()
//  }

  def fetchBlocksUpdateFeeResultInCache(local: Boolean): Future[Unit/*Result*/] = {
    val futureUsdPrice = blockchainConnector.getUsdPrice
    futureUsdPrice.flatMap { usdPrice => {
      val futureValRichBlocks = blockchainConnector.getBlocks
      futureValRichBlocks.onComplete{ _ match {
        case Success(result) => result match {
          case Valid(jsonBlocks) =>
            //logger.info(s"BlocksController: obtained ${jsonBlocks.blocks.size} blocks")
          case Invalid(y) =>
            logger.info(s"BlocksController: error when getting blocks: ${y.message}")
        }
        case Failure(e) =>
          logger.info("BlocksController: exception when getting blocks", e)
      }
      }
      val futSeqValidated = futureValRichBlocks.flatMap(enrichBlocks(_, local))
      futSeqValidated.map { seqValidated =>
        val valid = seqValidated.collect { case Valid(richBlockEntry) => richBlockEntry }
        valid match {
          case Nil => {
            //logger.info(s"BlocksController: adding empty FeeResult to cache")
            cache.set("feeresult", cache.get[FeeResult]("feeresult").getOrElse(FeeResult.empty).copy(usdPrice = usdPrice))
//            Ok(rich_blocks_empty_template(""))
          }
          case l => {
            val all = l.flatMap(_.block.tx)
            val (maxBlock, maxBlockFee) = (for {
              (b,height) <- l.map(a => (a.block, a.blockId.height))
            } yield {(height, b.tx.headOption.map(a => -a.fees).getOrElse(-1L))}).maxBy(a => a._1)
            logger.info("")
            logger.info("")
            logger.info(s"reward for block $maxBlock is ${(BigDecimal(maxBlockFee)./(BigDecimal(100000000))).setScale(8)} BTC")
            logger.info("")
            logger.info("")
            logger.info(s"BlocksController: adding FeeResult from ${all.size} transactions to cache ")
            cache.set("feeresult", FeeResult.fromTransactions(Transactions(all), l.exists(_.block.isEmpty), usdPrice, BigDecimal(maxBlockFee)))
//            Ok(rich_blocks_template("", RichBlocks(l), counter))
          }
        }
      }
    }
    }
  }

  def enrichBlocks(blocks: Validated[BlockReaderError, JsonBlocks], local: Boolean)(implicit mat: Materializer): Future[Seq[Validated[BlockReaderError, RichBlock]]] = {
    blocks match {
      case Valid(bl) =>
        produceRichBlockEntries(bl, local)
      case Invalid(e) =>Future.successful(Seq(Invalid(e)))
    }
  }

  private def produceRichBlockEntries(bl: JsonBlocks, local: Boolean)(implicit mat: Materializer): Future[Seq[Valid[RichBlock]]] = {
    val source = Source.apply[JsonBlockEntry](bl.blocks.toList)
      .throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.Shaping)

    val richBlockEntrySource = source.mapAsync(parallelism = 3) { jsonBlockEntry =>
      cache.get[RichBlock](jsonBlockEntry.height.toString) match {
        case Some(richBlockEntry) =>
          //logger.info(s"got from cache block ${jsonBlockEntry.height}")
          Future.successful(Valid(richBlockEntry))
        case _ => {
          val response = blockchainConnector.getBlock(jsonBlockEntry.hash, jsonBlockEntry.height, local)
          response map {
            case Valid(jb) =>
              val rbe = RichBlock(jsonBlockEntry.toBlockId, jb.toBlock)
              cache.set(jsonBlockEntry.height.toString, rbe)
              logger.info(s"added to cache block ${jsonBlockEntry.height}   ${LocalDateTime.ofEpochSecond(jsonBlockEntry.time, 0, ZoneOffset.UTC).toString}")
              Valid(rbe)
            case Invalid(e) =>
              logger.info(s"invalid block entry ${jsonBlockEntry.height} ${e.message}")
              Valid(RichBlock(jsonBlockEntry.toBlockId, EmptyBlock))
          }
        }
      }
    }

    val richBlockEntries: Future[Seq[Valid[RichBlock]]] =
      richBlockEntrySource.runWith(Sink.seq)
    if (bl.blocks.nonEmpty) {
      logger.info(s"sequence of ${bl.blocks.size} block requests: ${bl.blocks.map(_.height)}")
    }
    richBlockEntries
  }
}
