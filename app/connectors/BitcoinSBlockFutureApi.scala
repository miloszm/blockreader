package connectors

import java.net.URI

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import model.BlockReaderError
import model.json.{JsonBlock, JsonInput, JsonOutput, JsonTransaction}
import org.bitcoins.core.crypto.DoubleSha256DigestBE
import org.bitcoins.rpc.jsonmodels.GetRawTransactionVin

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


object BitcoinSBlockFutureApi extends App {
  import org.bitcoins.core.config._
  import org.bitcoins.rpc.client.common._
  import org.bitcoins.rpc.config._

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

  def processTransactionInputs(rpcCli: BitcoindRpcClient, inputs: Seq[GetRawTransactionVin]): Seq[JsonInput] = {
    for {
      i <- inputs
      txid <- i.txid
      ii <- i.vout
    } yield {
      val prevTransactionFuture = rpcCli.getRawTransaction(txid)
      val prevTransaction = Await.result(prevTransactionFuture, 20 seconds)
      val prevOut = prevTransaction.vout(ii)
      prevOut.value.toBigDecimal
      JsonInput(Some(JsonOutput(Some(prevOut.value.toBigDecimal.toLong), Some("" + prevOut.scriptPubKey.addresses), Some(prevOut.scriptPubKey.asm))))
    }
  }

  def getMhmBlock(blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
    getMhmBlockWithClient(rpcCli, blockHash, blockHeight)
  }

  def getMhmBlockWithClient(rpcCli: BitcoindRpcClient, blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
    val h = DoubleSha256DigestBE(blockHash)
    val blockFuture = rpcCli.getBlockRaw(h)
    var i = 0
    val blockResponse = Await.result(blockFuture, 20 seconds)
//    blockFuture.map { blockResponse =>
      val jsonTransactions = blockResponse.transactions.map { t =>
        val transactionFuture = rpcCli.getRawTransaction(t.txIdBE)
        val transaction = Await.result(transactionFuture, 20 seconds)
        print("."); i = i + 1
        if (i % 200 == 0) println
        val inputs = processTransactionInputs(rpcCli, transaction.vin)
        val outputs = transaction.vout.map{ o =>
          JsonOutput(Some(o.value.satoshis.toLong), Some("" + o.scriptPubKey.addresses), Some(o.scriptPubKey.asm))
        }
        JsonTransaction(inputs, outputs, 0, inputs.size, outputs.size, transaction.hash.hex, transaction.size, transaction.time.map(_.toLong).getOrElse(0L))
      }
      val fee = jsonTransactions.map{ t =>
        t.inputs.flatMap(_.prev_out).flatMap(_.value).sum - t.out.flatMap(_.value).sum
      }.sum
      Future.successful(Valid(JsonBlock(fee, blockHeight, blockResponse.txCount.toInt, jsonTransactions, blockResponse.blockHeader.time.toInt)))
//    }.recover{ case e =>
//      Invalid(BlockReaderError(1, e.getMessage))
//    }
  }

  val r = Await.result(getMhmBlock("00000000000000000010b5ded3990b193dbc359cb5525a2e95e94491f1dea590", 0), 500 seconds)
  println(r)


  System.exit(1)
}
