package model

import org.joda.time.LocalTime

case class JsonBlocks(blocks: Seq[JsonBlockEntry]){
  def toBlocks = Blocks(this.blocks.map(_.toBlockEntry))
}

case class JsonBlockEntry(height:Int, hash: String, time: Long){
  def toBlockEntry = BlockEntry(this.height, this.hash, new LocalTime(this.time * 1000))
}

case class JsonBlock(fee: Long, height: Long, n_tx: Int){
  def toBlock = Block(fee, height, n_tx)
}


case class Blocks(blocks: Seq[BlockEntry])

case class BlockEntry(height:Int, hash: String, time: LocalTime)

case class Block(fee: Long, height: Long, n_tx: Int)

