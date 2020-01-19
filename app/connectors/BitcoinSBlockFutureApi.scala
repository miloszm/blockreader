package connectors

import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import model.BlockReaderError
import model.json._
import org.bitcoins.core.crypto.DoubleSha256DigestBE
import org.bitcoins.core.protocol.transaction.Transaction
import org.bitcoins.rpc.jsonmodels.GetRawTransactionVin

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import javax.inject.Singleton

@Singleton
class BitcoinSBlockFutureApi {
  import org.bitcoins.core.config._
  import org.bitcoins.rpc.client.common._
  import org.bitcoins.rpc.config._

  implicit val system = ActorSystem("blockreader")
  //implicit val materializer = ActorMaterializer()


//  val username = "foo" //this username comes from 'rpcuser' in your bitcoin.conf file
//  val password = "bar" //this password comes from your 'rpcpassword' in your bitcoin.conf file
//
//  val authCredentials = BitcoindAuthCredentials.PasswordBased(
//    username = username,
//    password = password
//  )
//
//  val bitcoindInstance = {
//    BitcoindInstance (
//      network = MainNet,
//      uri = new URI(s"http://localhost:${MainNet.port}"),
//      rpcUri = new URI(s"http://localhost:${MainNet.rpcPort}"),
//      authCredentials = authCredentials
//    )
//  }

  implicit val ec: ExecutionContext = ExecutionContext.global

  //lazy val rpcCli = BitcoindRpcClient(bitcoindInstance)

  def processTransactionInputs(rpcCli: BitcoindRpcClient, inputs: Seq[GetRawTransactionVin])(implicit mat: Materializer): Future[Seq[JsonInput]] = {
    case class Inp(txid: DoubleSha256DigestBE, idx: Int)
    val inps = for {
      i <- inputs
      txid <- i.txid
      ii <- i.vout
    } yield {
      Inp(txid, ii)
    }

    val inpSource = Source.apply[Inp](inps.toList)
    val s = inpSource.mapAsync(parallelism = 3) { inp =>
      rpcCli.getRawTransaction(inp.txid).map { prevTransaction =>
        val prevOut = prevTransaction.vout(inp.idx)
        JsonInput(Some(JsonOutput(Some(prevOut.value.satoshis.toLong), Some("" + prevOut.scriptPubKey.addresses), Some(prevOut.scriptPubKey.asm))))
      }
    }.runWith(Sink.seq)
    s
  }

//  def getMhmBlock(blockHash: String, blockHeight: Int): Future[Validated[BlockReaderError, JsonBlock]] = {
//    getMhmBlockWithClient(rpcCli, blockHash, blockHeight)
//  }

  def getMhmBlockWithClient(rpcCli: BitcoindRpcClient, blockHash: String, blockHeight: Int)(implicit mat: Materializer): Future[Validated[BlockReaderError, JsonBlock]] = {
    val h = DoubleSha256DigestBE(blockHash)
    val blockFuture = rpcCli.getBlockRaw(h)
    var i = 0
    val blockResponse = Await.result(blockFuture, 20 seconds)

    println(s"Getting locally block: $blockHeight of size ${blockResponse.transactions.toList.size}")
    val transactionsSource = Source.apply[Transaction](blockResponse.transactions.toList)
      //.throttle(50, FiniteDuration(1, TimeUnit.SECONDS), 50, ThrottleMode.Shaping)

    val jsonTransactionsFuture = transactionsSource.mapAsync(parallelism = 4) { t =>
      rpcCli.getRawTransaction(t.txIdBE).flatMap { transactionResult =>
        val nOfInputs = transactionResult.vin.size
        if (i % 50 == 0) print(s" $i ")
        i = i + 1
        val inputsFuture = processTransactionInputs(rpcCli, transactionResult.vin)
        inputsFuture.map { inputs =>
          val outputs = transactionResult.vout.map { o =>
            JsonOutput(Some(o.value.satoshis.toLong), o.scriptPubKey.addresses.map(_.mkString(",")), Some(o.scriptPubKey.asm))
          }
          JsonTransaction(inputs, outputs, 0, inputs.size, outputs.size, transactionResult.txid.hex, transactionResult.size, transactionResult.time.map(_.toLong).getOrElse(0L))
        }
      }
    }.runWith(Sink.seq)
    jsonTransactionsFuture.map { jsonTransactions =>
      Valid(JsonBlock(0, blockHeight, blockResponse.txCount.toInt, jsonTransactions, blockResponse.blockHeader.time.toInt))
    }.recover{ case e =>
    Invalid(BlockReaderError(1, e.getMessage))
    }
  }


//  val r = Await.result(getMhmBlock("00000000000000000010b5ded3990b193dbc359cb5525a2e95e94491f1dea590", 0), 500 seconds)
//  println(r)
//
//
//  System.exit(1)
}
