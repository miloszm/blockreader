package model

import java.util.Date

import org.joda.time.{DateTimeZone, LocalTime}

case class JsonBlocks(blocks: Seq[JsonBlock]){
  def toBlocks = Blocks(this.blocks.map(_.toBlock))
}

case class JsonBlock(height:Int, hash: String, time: Long){
  def toBlock = Block(this.height, this.hash, new LocalTime(this.time * 1000))
}

case class Blocks(blocks: Seq[Block])

case class Block(height:Int, hash: String, time: LocalTime)
