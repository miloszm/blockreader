package connectors

import java.net.URI

import org.bitcoins.core.crypto.{DoubleSha256Digest, DoubleSha256DigestBE}
import org.bitcoins.core.protocol.BitcoinAddress
import org.bitcoins.core.protocol.blockchain.Block
import org.bitcoins.core.protocol.transaction.EmptyTransaction

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

  def getTransactionDemo(txId: String): Unit = {
    def printAddresses(prefix: String, addresses: Option[Vector[BitcoinAddress]]): Unit =
      addresses.foreach { addrs =>
        println(s"$prefix${addrs.mkString(",")}")
      }
    val transactionFuture = rpcCli.getRawTransaction(DoubleSha256DigestBE(txId))
    val transaction = Await.result(transactionFuture, 20 seconds)
    println("=" * 80)
    println(s"txid=${transaction.txid.hex}")
    println(s"blockhash=${transaction.blockhash.map(_.hex).getOrElse("")}")
    println(s"confirmations=${transaction.confirmations.getOrElse(0)}")
    val totalOut = transaction.vout.map(_.value.toBigDecimal).sum
    println(s"total out=$totalOut")
    transaction.vout.foreach { a =>
      println(s"  out ${a.n}")
      println(s"     value: ${a.value}")
      println(s"     script type: ${a.scriptPubKey.scriptType}")
      println(s"     script asm: ${a.scriptPubKey.asm}")
      println(s"     script hex: ${a.scriptPubKey.hex}")
      printAddresses("     address: ", a.scriptPubKey.addresses)
    }
    val totalIn = for {
        (in, index) <- transaction.vin.zipWithIndex
      }
      yield {
          println(s"  in $index")
          println(s"     vout: ${in.vout.getOrElse(-1)}")
          println(s"     sequence: ${in.sequence.getOrElse(-1)}")
          println(s"     witness: ${in.txinwitness.getOrElse("")}")
          println(s"     txid: ${in.txid.map(_.hex).getOrElse("")}")
          in.txid.fold(BigDecimal(0)){ prevTxid =>
            val prevTransactionFuture = rpcCli.getRawTransaction(prevTxid)
            val prevTransaction = Await.result(prevTransactionFuture, 20 seconds)
            in.vout.foreach { inVout =>
              println(s"         value: ${prevTransaction.vout(inVout).value}")
              printAddresses("         address: ", prevTransaction.vout(inVout).scriptPubKey.addresses)
            }
            in.vout.map(prevTransaction.vout(_).value.toBigDecimal).getOrElse(BigDecimal(0))
          }
      }
    println(s"fee: ${totalIn.sum - totalOut}")
  }

  getTransactionDemo("a88d37b18624f2ff8853e51a0a7fb1b005ca5c8621c8b2a56207d35b00141974")
//  getTransactionDemo("592ca3dc4e1e7a659e480df192968c3ade8f64b8c26e997960676d5e8150722c")

  System.exit(1)
}
