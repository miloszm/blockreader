package services

import java.time.Clock

import javax.inject.{Inject, Singleton}
import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import controllers.BlocksController
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{Await, Future, duration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@Singleton
class GlobalScheduler @Inject() (clock: Clock, appLifecycle: ApplicationLifecycle, blocksController: BlocksController) {

  val logger = Logger

  class BlockPoller extends Actor {
    var local = false
    override def receive: Actor.Receive = {
      case _:String => {
        if (!local){
          logger.info("BlockPoller actor: running non-local block fetch")
        } else {
          logger.info("BlockPoller actor: running local block fetch")
        }

        val fut = Try {blocksController.fetchBlocksUpdateFeeResultInCache(local)} match {
          case Success(f) => f
          case Failure(t) =>
            logger.info("BlockPoller actor: error in block fetch", t)
            Future.successful(())
        }
        Await.result[Unit](fut, Duration(30, duration.HOURS))
        logger.info("BlockPoller actor: got response from block fetch")
        local = true
        sender ! "answer"
      }
    }
  }

  object BlockPoller {
    def props = Props(new BlockPoller)
  }

  val blockPoller = blocksController.system.actorOf(BlockPoller.props, name = "blockpoller")
  def askPollerAndWait: Unit = {
    implicit val timeout = Timeout(30 hours)
    logger.info("GlobalScheduler: sending message a to the poller actor")
    val fut = blockPoller ? "a"
    Await.result[Any](fut, Duration(30, duration.HOURS))
    logger.info("GlobalScheduler: received response from the poller actor")
    logger.info("GlobalScheduler: scheduling poller asker in 30 seconds")
    blocksController.system.scheduler.scheduleOnce(30.seconds)(askPollerAndWait)
  }
  // comment out to turn off
  logger.info("GlobalScheduler: initial scheduling of the poller asker in 5 seconds")
  blocksController.system.scheduler.scheduleOnce(5.seconds)(askPollerAndWait)

}

