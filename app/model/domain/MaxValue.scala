package model.domain

case class MaxValue(value: Long, address: String, script: String)

object MaxValue {

  implicit object MaxValueOrdering extends Ordering[MaxValue] {
    override def compare(x: MaxValue, y: MaxValue) =
      if (x.value < y.value) -1
      else if (x.value == y.value) 0
      else 1
  }

}
