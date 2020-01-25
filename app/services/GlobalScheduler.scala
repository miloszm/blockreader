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

  val logger = Logger("blockreader")

  class BlockPoller extends Actor {
    var local = false
    override def receive: Actor.Receive = {
      case _:String => {
        if (!local){
          logger.info("scheduled non-local block fetch")
        }
        val fut = Try {blocksController.fetchBlocksUpdateFeeResultInCache(local)} match {
          case Success(f) => f
          case Failure(t) =>
            logger.info("error in block poller", t)
            Future.successful(())
        }
        Await.result[Unit](fut, Duration(30, duration.HOURS))
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
    val fut = blockPoller ? "a"
    Await.result[Any](fut, Duration(30, duration.HOURS))
    blocksController.system.scheduler.scheduleOnce(30.seconds)(askPollerAndWait)
  }
  // comment out to turn off
  blocksController.system.scheduler.scheduleOnce(10.seconds)(askPollerAndWait)

}

