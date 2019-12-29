package model.json

import model.domain.BlockId
import org.joda.time.LocalTime

case class JsonBlockEntry(height: Int, hash: String, time: Long) {
  def toBlockId = BlockId(this.height, this.hash, new LocalTime(this.time * 1000))
}
