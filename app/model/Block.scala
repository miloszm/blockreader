package model

import org.joda.time.LocalTime

case class JsonBlocks(blocks: Seq[JsonBlockEntry]){
  def toBlocks = Blocks(this.blocks.map(_.toBlockEntry))
}

case class JsonBlockEntry(height:Int, hash: String, time: Long){
  def toBlockEntry = BlockEntry(this.height, this.hash, new LocalTime(this.time * 1000))
}

case class JsonBlock(fee: Long, height: Long, n_tx: Int, tx: Seq[JsonTransaction]){
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

case class JsonTransaction(inputs: Seq[JsonInput], out: Seq[JsonOutput], tx_index: Long){
  def toTransaction = Transaction(inputs.flatMap(_.toInput), out.flatMap(_.toOutput), tx_index)
}



case class Output(value: Long)

case class Input(value: Long)

case class Transaction(inputs: Seq[Input], outputs: Seq[Output], index: Long){
  def sumInputs: Long = inputs.map(_.value).sum
  def sumOutputs: Long = outputs.map(_.value).sum
  def fees = sumInputs - sumOutputs
}

case class Blocks(blocks: Seq[BlockEntry])

case class BlockEntry(height:Int, hash: String, time: LocalTime)

case class Block(fee: Long, height: Long, n_tx: Int, tx: Seq[Transaction]){
  def fees = tx.map(_.fees).filter(_ > 0)
  val feesSize = n_tx-1
  def sumFees: Long = fees.sum
  def avgFee = sumFees / feesSize
  def maxFee = fees.max
  def minFee = fees.min
  def medianFee  =
  {
    val (lower, upper) = fees.sortWith(_<_).splitAt(feesSize / 2)
    if (feesSize % 2 == 0) (lower.last + upper.head) / 2 else upper.head
  }
  def sumOutputs = tx.map(_.sumOutputs).sum
}

