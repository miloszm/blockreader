package connectors

import java.net.URI

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

object BitcoinSConnector extends App {

  import org.bitcoins.core.config._
  import org.bitcoins.rpc.config._
  import org.bitcoins.rpc.client.common._

  val username = "foo" //this username comes from 'rpcuser' in your bitcoin.conf file
  val password = "bar" //this password comes from your 'rpcpassword' in your bitcoin.conf file

  val authCredentials = BitcoindAuthCredentials.PasswordBased(
    username = username,
    password = password
  )

  val bitcoindInstance = {
    BitcoindInstance (
      network = MainNet,
      uri = new URI(s"http://localhost:${MainNet.port}"),
      rpcUri = new URI(s"http://localhost:${MainNet.rpcPort}"),
      authCredentials = authCredentials
    )
  }

  implicit val ec: ExecutionContext = ExecutionContext.global

  val rpcCli = BitcoindRpcClient(bitcoindInstance)

  def getInfo() = {
    val infoFuture = rpcCli.getBlockChainInfo
    val info = Await.result(infoFuture, 20 seconds)
    println(s"${info}")
    println(s"${info.blocks}")
  }

  getInfo()

}
