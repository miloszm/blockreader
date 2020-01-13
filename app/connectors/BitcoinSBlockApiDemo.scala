package connectors

import java.net.URI

import org.bitcoins.core.crypto.{DoubleSha256Digest, DoubleSha256DigestBE, Sha256Hash160Digest}
import org.bitcoins.rpc.jsonmodels.GetRawTransactionVin

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


object BitcoinSBlockApiDemo extends App {
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

  def sumTransactionInputs(inputs: Seq[GetRawTransactionVin]): BigDecimal = {
    (for {
      i <- inputs
      txid <- i.txid
      ii <- i.vout
    } yield {
      val prevTransactionFuture = rpcCli.getRawTransaction(txid)
      val prevTransaction = Await.result(prevTransactionFuture, 20 seconds)
      val prevOut = prevTransaction.vout(ii)
      prevOut.value.toBigDecimal
    }).sum
  }

  def getBlockRaw(blockHash: String): Unit = {
    val h = DoubleSha256DigestBE(blockHash)
    val blockFuture = rpcCli.getBlockRaw(h)
    val block = Await.result(blockFuture, 20 seconds)
    println(s"block hash: ${block.blockHeader.hashBE.hex}")
    println(s"block number of transactions: ${block.txCount.toInt}")
    println(s"block weight: ${block.blockWeight}")
    block.transactions.foreach { t =>
      val transactionFuture = rpcCli.getRawTransaction(t.txIdBE)
      val transaction = Await.result(transactionFuture, 20 seconds)
      val sumInputs = sumTransactionInputs(transaction.vin)
      val sumOutputs = t.outputs.map(_.value.toBigDecimal).sum / BigDecimal(100000000)
      println(s"${t.txIdBE.hex} $sumOutputs \t\t $sumInputs \t\t ${sumInputs-sumOutputs}")
    }
  }

  getBlockRaw("00000000000000000010b5ded3990b193dbc359cb5525a2e95e94491f1dea590")


  System.exit(1)
}
