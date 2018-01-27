package services

import java.time.Clock
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import controllers.BlocksController
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{Await, duration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@Singleton
class GlobalScheduler @Inject() (clock: Clock, appLifecycle: ApplicationLifecycle, blocksController: BlocksController) {

  class BlockPoller extends Actor {
    override def receive: Actor.Receive = {
      case _:String => {
        val fut = blocksController.fetchBlocksUpdateFeeResultInCache()
        Await.result[Unit](fut, Duration(30, duration.MINUTES))
        sender ! "answer"
      }
    }
  }

  object BlockPoller {
    def props = Props(new BlockPoller)
  }

  val blockPoller = blocksController.system.actorOf(BlockPoller.props, name = "blockpoller")
  def askPollerAndWait: Unit = {
    implicit val timeout = Timeout(30 minutes)
    val fut = blockPoller ? "a"
    Await.result[Any](fut, Duration(30, duration.MINUTES))
  }
  blocksController.system.scheduler.schedule(10.seconds, 30.seconds)(askPollerAndWait)

}

