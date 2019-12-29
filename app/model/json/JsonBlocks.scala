package model.json

import cats.Semigroup
import model.Blocks

case class JsonBlocks(blocks: Seq[JsonBlockEntry]) {
  def toBlocks = Blocks(this.blocks.map(_.toBlockEntry))
  def height = this.blocks.map(_.height).max
  def signature = s"${blocks.lastOption.map(_.height.toString)}..${blocks.headOption.map(_.height.toString)}"
}

object JsonBlocks extends Semigroup[JsonBlocks] {
  override def combine(x: JsonBlocks, y: JsonBlocks) = JsonBlocks(x.blocks ++ y.blocks)
}
