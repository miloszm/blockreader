package model

import org.joda.time.LocalTime

case class JsonBlocks(blocks: Seq[JsonBlockEntry]){
  def toBlocks = Blocks(this.blocks.map(_.toBlockEntry))
}

case class JsonBlockEntry(height:Int, hash: String, time: Long){
  def toBlockEntry = BlockEntry(this.height, this.hash, new LocalTime(this.time * 1000))
}

case class JsonBlock(fee: Long, height: Long, n_tx: Int, tx: Seq[JsonTransaction]) {
  def toBlock = Block(fee, height, n_tx, tx.map(_.toTransaction))
}


case class JsonOutput(value: Option[Long]){
  def toOutput = value.map(v => Output(v))
}

case class JsonInput(prev_out: Option[JsonOutput]){
  def toInput = for {
    prev <- prev_out
    v <- prev.value
  } yield Input(v)
}

case class JsonTransaction(inputs: Seq[JsonInput], out: Seq[JsonOutput], tx_index: Long, vin_sz: Int, vout_sz: Int, hash: String){
  def toTransaction = Transaction(inputs.flatMap(_.toInput), out.flatMap(_.toOutput), tx_index, hash)
}



case class Output(value: Long)

case class Input(value: Long)

case class Transaction(inputs: Seq[Input], outputs: Seq[Output], index: Long, hash: String){
  def sumInputs: Long = inputs.map(_.value).sum
  def sumOutputs: Long = outputs.map(_.value).sum
  def fees = sumInputs - sumOutputs
  def numOutputs = outputs.size
}

case class Blocks(blocks: Seq[BlockEntry])

case class BlockEntry(height:Int, hash: String, time: LocalTime)

sealed trait BlockTrait{
  def tx: Seq[Transaction]
  def n_tx: Int
  def fees = tx.map(_.fees).filter(_ > 0)
  val feesSize = if (n_tx > 0) n_tx-1 else 0
  def sumFees: Long = if (feesSize == 0) 0L else fees.sum
  def avgFee = if (feesSize == 0) 0L else sumFees / feesSize
  def maxFee = if (feesSize == 0) 0L else fees.max
  def minFee = if (feesSize == 0) 0L else fees.min
  def medianFee  = if (feesSize == 0) 0L else
  {
    val (lower, upper) = fees.sortWith(_<_).splitAt(feesSize / 2)
    if (feesSize % 2 == 0) (lower.last + upper.head) / 2 else upper.head
  }
  def sumOutputs = tx.map(_.sumOutputs).sum
}

case class Block(fee: Long, height: Long, n_tx: Int, tx: Seq[Transaction]) extends BlockTrait
object EmptyBlock extends BlockTrait {
  def tx = Nil
  def n_tx = 0
}

case class RichBlocks(blocks: Seq[RichBlockEntry])

case class RichBlockEntry(blockEntry: BlockEntry, block: BlockTrait)
