package connectors

import java.net.URI

import javax.inject.Singleton
import org.bitcoins.core.crypto.DoubleSha256Digest
import org.bitcoins.core.protocol.blockchain.Block
import org.bitcoins.rpc.jsonmodels.GetBlockResult

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class BitcoinSConnector {

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

  def getLatestBlock: Future[Int] = rpcCli.getBlockChainInfo.map(_.blocks)

  def getBlock(blockHash: String): Future[Block] = {
    val h = DoubleSha256Digest(blockHash)
    rpcCli.getBlockRaw(h)
  }

}
