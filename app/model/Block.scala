package model

import java.time.LocalDateTime

import cats.Semigroup
import connectors.BlockchainConnector
import org.joda.time.LocalTime

case class JsonBlocks(blocks: Seq[JsonBlockEntry]) {
  def toBlocks = Blocks(this.blocks.map(_.toBlockEntry))
  def height = this.blocks.map(_.height).max
  def signature = s"${blocks.lastOption.map(_.height.toString)}..${blocks.headOption.map(_.height.toString)}"
}

object JsonBlocks extends Semigroup[JsonBlocks] {
  override def combine(x: JsonBlocks, y: JsonBlocks) = JsonBlocks(x.blocks ++ y.blocks)
}

case class JsonBlockEntry(height: Int, hash: String, time: Long) {
  def toBlockEntry = BlockEntry(this.height, this.hash, new LocalTime(this.time * 1000))
}

case class JsonBlock(fee: Long, height: Long, n_tx: Int, tx: Seq[JsonTransaction], time: Long) {
  def toBlock = Block(fee, height, n_tx, tx.map(_.toTransaction), time)
}

case class JsonOutput(value: Option[Long]) {
  def toOutput = value.map(v => Output(v))
}

case class JsonInput(prev_out: Option[JsonOutput]) {
  def toInput = for {
    prev <- prev_out
    v <- prev.value
  } yield Input(v)
}

case class JsonTransaction(
  inputs: Seq[JsonInput],
  out: Seq[JsonOutput],
  tx_index: Long,
  vin_sz: Int,
  vout_sz: Int,
  hash: String,
  size: Int,
  time: Long
) {
  def toTransaction = Transaction(inputs.flatMap(_.toInput), out.flatMap(_.toOutput), tx_index, hash, size, time)
}

case class Output(value: Long)

case class Input(value: Long)

case class Transaction(inputs: Seq[Input], outputs: Seq[Output], index: Long, hash: String, size: Int, time: Long) {
  def sumInputs: Long = inputs.map(_.value).sum
  def sumOutputs: Long = outputs.map(_.value).sum
  def fees = sumInputs - sumOutputs
  def numOutputs = outputs.size
  def feePerByte: Long = if (size == 0 || fees < 0) 0 else fees / size
  def age(t: Long): Long = t - time
  def ageInBlocks(t: Long): Long = age(t) / 600
}

case class Blocks(blocks: Seq[BlockEntry])

case class LatestBlock(height: Int)

case class BlockEntry(height: Int, hash: String, time: LocalTime)

sealed trait BlockTrait {
  def isEmpty = false
  def tx: Seq[Transaction]
  def n_tx: Int
  def time: Long
  def fees = tx.map(_.fees).filter(_ > 0)
  val feesSize = if (n_tx > 0) n_tx - 1 else 0
  def sumFees: Long = if (feesSize == 0) 0L else fees.sum
  def avgFee = if (feesSize == 0) 0L else sumFees / feesSize
  def maxFee = if (feesSize == 0) 0L else fees.max
  def minFee = if (feesSize == 0) 0L else fees.min
  def medianFee: Long = if (feesSize == 0) 0L else {
    val (lower, upper) = fees.sortWith(_ < _).splitAt(feesSize / 2)
    if (feesSize % 2 == 0) (lower.last + upper.head) / 2 else upper.head
  }
  def sumOutputs = tx.map(_.sumOutputs).sum
  def avgSize: BigDecimal = if (feesSize > 0) tx.drop(1).map(_.size).sum / feesSize else 0
  def minSize: Int = if (feesSize > 0) tx.drop(1).map(_.size).min else 0
  def maxSize: Int = if (feesSize > 0) tx.drop(1).map(_.size).max else 0
  def medianSize: BigDecimal = if (feesSize == 0) 0L else {
    val (lower, upper) = tx.drop(1).map(_.size).sortWith(_ < _).splitAt(feesSize / 2)
    if (feesSize % 2 == 0) (lower.last + upper.head) / 2 else upper.head
  }
  def avgFeePerByte: Long = StatCalc.avg(tx.drop(1).map(_.feePerByte))
  def medianFeePerByte: Long = StatCalc.median(tx.drop(1).map(_.feePerByte))
  def avgFeePerByteNoWait: Long = if (feesSize == 0) 0L else {
    val fastTransactions = tx.drop(1).filter(_.ageInBlocks(time) == 0)
    fastTransactions.map(_.feePerByte).sum / fastTransactions.size
  }
  def medianFeePerByteNoWait: Long = if (feesSize == 0) 0L else {
    val fastTransactions = tx.drop(1).filter(_.ageInBlocks(time) == 0)
    StatCalc.median(fastTransactions.map(_.feePerByte))
  }
  def avgFeePerByteWaitOne: Long = if (feesSize == 0) 0L else {
    val fastTransactions = tx.drop(1).filter(_.ageInBlocks(time) == 1)
    StatCalc.avg(fastTransactions.map(_.feePerByte))
  }
}

case class Block(fee: Long, height: Long, n_tx: Int, tx: Seq[Transaction], time: Long) extends BlockTrait
object EmptyBlock extends BlockTrait {
  def tx = Nil
  def n_tx = 0
  def time = 0
  override def isEmpty = true
}

case class RichBlocks(blocks: Seq[RichBlockEntry]){
  def avgAvgFeePerByte: Long = StatCalc.avg(blocks.map(_.block).filterNot(_.isEmpty).map(_.avgFeePerByte))
  def medianMedianFeePerByte: Long = StatCalc.median(blocks.map(_.block).filterNot(_.isEmpty).map(_.medianFeePerByte))
  def avgAvgFeePerByteNoWait: Long = StatCalc.avg(blocks.map(_.block).filterNot(_.isEmpty).map(_.avgFeePerByteNoWait))
  def medianMedianFeePerByteNoWait: Long = StatCalc.median(blocks.map(_.block).filterNot(_.isEmpty).map(_.medianFeePerByteNoWait))
  def avgAvgFeePerByteWaitOne: Long = StatCalc.avg(blocks.map(_.block).filterNot(_.isEmpty).map(_.avgFeePerByteWaitOne))
  def totalAvgFeePerByteNoWait: Long = {
    val blcks = blocks.map(_.block).filterNot(_.isEmpty)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks(block.time) == 0)
    }
    StatCalc.avg(notWaitingTransactions.map(_.feePerByte))
  }
  def last24hMedianFeePerByteNoWait: Long = {
    val now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
    val blcks = blocks.map(_.block).filterNot(_.isEmpty).filter(now - _.time*1000 < 24*3600*1000)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks(block.time) == 0)
    }
    StatCalc.median(notWaitingTransactions.map(_.feePerByte))
  }
  def last2hMedianFeePerByteNoWait: Long = {
    val now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
    val blcks = blocks.map(_.block).filterNot(_.isEmpty).filter(now - _.time*1000 < 2*3600*1000)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks(block.time) == 0)
    }
    StatCalc.median(notWaitingTransactions.map(_.feePerByte))
  }
}

case class RichBlockEntry(blockEntry: BlockEntry, block: BlockTrait)

object StatCalc {
  def avg(coll: Seq[Long]): Long =
    if (coll.isEmpty) 0 else coll.sum / coll.size
  def median(coll: Seq[Long]): Long = {
    if (coll.isEmpty) 0 else {
      val (lower, upper) = coll.sortWith(_ < _).splitAt(coll.size / 2)
      if (coll.size % 2 == 0) (lower.last + upper.head) / 2 else upper.head
    }
  }
}
