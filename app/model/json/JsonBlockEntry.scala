package model.json

import model.BlockEntry
import org.joda.time.LocalTime

case class JsonBlockEntry(height: Int, hash: String, time: Long) {
  def toBlockEntry = BlockEntry(this.height, this.hash, new LocalTime(this.time * 1000))
}
