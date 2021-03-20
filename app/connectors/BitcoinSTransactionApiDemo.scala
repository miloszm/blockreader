package connectors

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.URI

import org.bitcoins.crypto.{DoubleSha256Digest, DoubleSha256DigestBE, Sha256Hash160Digest}
import org.bitcoins.core.protocol.{Address, BitcoinAddress, P2PKHAddress}
import org.bitcoins.core.protocol.blockchain.Block
import org.bitcoins.core.protocol.script.ScriptPubKey
import org.bitcoins.core.protocol.transaction.{EmptyTransaction, Transaction}
import org.bitcoins.core.util.Base58

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try


//object BitcoinSTransactionApiDemo extends App {
//  import org.bitcoins.core.config._
//  import org.bitcoins.rpc.config._
//  import org.bitcoins.rpc.client.common._
//
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
//
//  implicit val ec: ExecutionContext = ExecutionContext.global
//
//  lazy val rpcCli = BitcoindRpcClient(bitcoindInstance)
//
//  def getInfo() = {
//    val infoFuture = rpcCli.getBlockChainInfo
//    val info = Await.result(infoFuture, 20 seconds)
//    println(s"${info}")
//    println(s"${info.blocks}")
//  }
//
//  getInfo()
//
//  def getLatestBlock: Future[Int] = rpcCli.getBlockChainInfo.map(_.blocks)
//
//  def getBlock(blockHash: String): Future[Block] = {
//    val h = DoubleSha256Digest(blockHash)
//    rpcCli.getBlockRaw(h)
//  }
//
//  def getTransactionDemo(txId: String): Unit = {
//    def printAddresses(prefix: String, addresses: Option[Vector[BitcoinAddress]]): Unit =
//      addresses.foreach { addrs =>
//        println(s"$prefix${addrs.mkString(",")}")
//      }
//    val thisTxSha = DoubleSha256DigestBE(txId)
//    val transactionFuture = rpcCli.getRawTransaction(thisTxSha)
//    val transaction = Await.result(transactionFuture, 20 seconds)
//    println("=" * 80)
//    println(s"txid=${transaction.txid.hex}")
//    println(s"hash=${transaction.hash}")
//    println(s"blockhash=${transaction.blockhash}")
//    println(s"size=${transaction.size}")
//    println(s"vsize=${transaction.vsize}")
//    println(s"weight=${transaction.hex.weight}")
//    println(s"base size=${transaction.hex.baseSize}")
//    println(s"version=${transaction.hex.version.hex}")
//    println(s"is coinbase=${transaction.hex.isCoinbase}")
//    println(s"blockhash=${transaction.blockhash.map(_.hex).getOrElse("")}")
//    println(s"confirmations=${transaction.confirmations.getOrElse(0)}")
//    val totalOut = transaction.vout.map(_.value.toBigDecimal).sum
//    println(s"total out=$totalOut")
//
//    /**
//      * outputs
//      */
//    transaction.vout.foreach { output =>
//      println(s"  out ${output.n}")
//      println(s"     value: ${output.value}")
//      println(s"     script type: ${output.scriptPubKey.scriptType}")
//      println(s"     script asm: ${output.scriptPubKey.asm}")
//      println(s"     script hex: ${output.scriptPubKey.hex}")
//      printAddresses("     address: ", output.scriptPubKey.addresses)
//      //println(s"     spent: ${Try(Await.result(rpcCli.getTxOut(thisTxSha, output.n), 20 seconds)).fold(_ => "yes", v => "no: available " + v.value)}")
//    }
//
//    /**
//      * inputs
//      */
//    val totalIn = for {
//        (input, index) <- transaction.vin.zipWithIndex
//      }
//      yield {
//          println(s"  in $index")
//          println(s"     vout: ${input.vout.getOrElse(-1)}")
//          println(s"     sequence: ${input.sequence.getOrElse(-1)}")
//          println(s"     script asm: ${input.scriptSig.map(_.asm).getOrElse("")}")
//          println(s"     script hex: ${input.scriptSig.map(_.hex).getOrElse("")}")
//          println(s"     txid: ${input.txid.map(_.hex).getOrElse("")}")
//          input.txid.fold(BigDecimal(0)){ prevTxid =>
//            val prevTransactionFuture = rpcCli.getRawTransaction(prevTxid)
//            val prevTransaction = Await.result(prevTransactionFuture, 20 seconds)
//            input.vout.fold[Unit](()){ inVout =>
//              val tout = prevTransaction.vout(inVout)
//              println(s"         value: ${tout.value}")
//              println(s"         script asm: ${tout.scriptPubKey.asm}")
//              println(s"         script hex: ${tout.scriptPubKey.hex}")
//              printAddresses("         address: ", tout.scriptPubKey.addresses)
//            }
//            input.vout.map(prevTransaction.vout(_).value.toBigDecimal).getOrElse(BigDecimal(0))
//          }
//      }
//    println(s"fee: ${totalIn.sum - totalOut}")
//  }
//
//  getTransactionDemo("a88d37b18624f2ff8853e51a0a7fb1b005ca5c8621c8b2a56207d35b00141974")
////  getTransactionDemo("592ca3dc4e1e7a659e480df192968c3ade8f64b8c26e997960676d5e8150722c")
////  val transWithUnspent = "e27ab49516f7b7b0a5bd5d7b4ced63b57ec590468aafe6c68f501cfeff79f3a6"
////  getTransactionDemo(transWithUnspent)
//
//  def conversionOfHash160ToBitcoinAddressDemo(): Unit = {
//    println
//    val hash160 = "528453ff8ee784f18b4014ab4f2bd74894eef65f"
//    println(s"for hash160=$hash160")
//    println(s"bitcoin address=${P2PKHAddress(Sha256Hash160Digest(hash160), MainNet)}")
//  }
//
//  def getTxOutProofDemo(): Unit = {
//    // want to prove that 0014f46aacf974d5ccf8e4f2f4ab561ea5523f7835fe or 17160014f46aacf974d5ccf8e4f2f4ab561ea5523f7835fe
//    // is a valid signature for
//    // public key hash: 528453ff8ee784f18b4014ab4f2bd74894eef65f
//    val hash160 = "528453ff8ee784f18b4014ab4f2bd74894eef65f"
//    val addr = P2PKHAddress(Sha256Hash160Digest(hash160), MainNet)
//    //rpcCli.verifyMessage(addr, "0014f46aacf974d5ccf8e4f2f4ab561ea5523f7835fe", )
//  }
//
//  conversionOfHash160ToBitcoinAddressDemo()
//
//  System.exit(1)
//}