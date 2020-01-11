package connectors

import java.net.URI

import org.bitcoins.core.crypto.{DoubleSha256Digest, DoubleSha256DigestBE}
import org.bitcoins.core.protocol.blockchain.Block

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object BitcoinSApiDemo extends App {
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

  lazy val rpcCli = BitcoindRpcClient(bitcoindInstance)

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

  def getTransactionDemo(txId: String, withInInfo: Boolean = true): Unit = {
    val transactionFuture = rpcCli.getRawTransaction(DoubleSha256DigestBE(txId))
    val transaction = Await.result(transactionFuture, 20 seconds)
    println("=" * 80)
    println(s"txid=${transaction.txid.hex}")
    println(s"blockhash=${transaction.blockhash.map(_.hex).getOrElse("")}")
    println(s"confirmations=${transaction.confirmations.getOrElse(0)}")
    println(s"total out=${transaction.vout.map(_.value.toBigDecimal).sum}")
    transaction.vout.foreach { a =>
      println(s"  out ${a.n}")
      println(s"     value: ${a.value}")
      println(s"     script type: ${a.scriptPubKey.scriptType}")
      println(s"     script asm: ${a.scriptPubKey.asm}")
      println(s"     script hex: ${a.scriptPubKey.hex}")
      a.scriptPubKey.addresses.map { addresses =>
        println(s"     address(es): ${addresses.mkString(",")}")
      }
    }
    if (withInInfo) {
      transaction.vin.foreach { a =>
        println(s"  in ${a.vout}")
        println(s"     script asm: ${a.scriptSig.map(_.asm).getOrElse("")}")
        println(s"     script hex: ${a.scriptSig.map(_.hex).getOrElse("")}")
        println(s"     vout: ${a.vout}")
        println(s"     sequence: ${a.sequence}")
        println(s"     witness: ${a.txinwitness}")
        println(s"     txid: ${a.txid.map(_.hex).getOrElse("")}")
      }
    }
  }

  getTransactionDemo("a88d37b18624f2ff8853e51a0a7fb1b005ca5c8621c8b2a56207d35b00141974")
  getTransactionDemo("592ca3dc4e1e7a659e480df192968c3ade8f64b8c26e997960676d5e8150722c", withInInfo = false)

  System.exit(1)
}
