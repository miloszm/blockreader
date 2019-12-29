package stats

object StatCalc {
  def avg(coll: Seq[Long]): Long =
    if (coll.isEmpty) 0 else coll.sum / coll.size
  def median(coll: Seq[Long]): Long = {
    if (coll.isEmpty) 0 else
    if (coll.size == 1) coll.head else {
      val (lower, upper) = coll.sortWith(_ < _).splitAt(coll.size / 2)
      if (coll.size % 2 == 0) (lower.last + upper.head) / 2 else upper.head
    }
  }
}
