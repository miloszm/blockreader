package model.json

import cats.Semigroup
import model.domain.BlockIds

case class JsonBlocks(blocks: Seq[JsonBlockEntry]) {
  def toBlockIds = BlockIds(this.blocks.map(_.toBlockId))
  def height = this.blocks.map(_.height).max
  def signature = s"${blocks.lastOption.map(_.height.toString)}..${blocks.headOption.map(_.height.toString)}"
}

object JsonBlocks extends Semigroup[JsonBlocks] {
  override def combine(x: JsonBlocks, y: JsonBlocks) = JsonBlocks((x.blocks.toSet ++ y.blocks.toSet).toSeq)
}
