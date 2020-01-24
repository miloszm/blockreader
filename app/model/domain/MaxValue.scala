package model.domain

import scala.math.BigDecimal.RoundingMode

case class MaxValue(value: Long, address: String, script: String){
  def valueAsStr: String = {
    val maxValue = BigDecimal(value) / BigDecimal(100000000)
    if (maxValue.equals(BigDecimal(0))) "0" else maxValue.setScale(8, RoundingMode.FLOOR).toString()
  }
}

object MaxValue {

  implicit object MaxValueOrdering extends Ordering[MaxValue] {
    override def compare(x: MaxValue, y: MaxValue) =
      if (x.value < y.value) -1
      else if (x.value == y.value) 0
      else 1
  }

}
