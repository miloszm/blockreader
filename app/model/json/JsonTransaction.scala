package model.json

import model.domain._

case class JsonOutput(value: Option[Long], hash: Option[String]) {
  def toOutput = value.map(v => Output(v))
}

case class JsonInput(prev_out: Option[JsonOutput]) {
  def toInput = for {
    prev <- prev_out
    v <- prev.value
  } yield Input(v)
}

case class JsonTransaction(
                            inputs: Seq[JsonInput],
                            out: Seq[JsonOutput],
                            tx_index: Long,
                            vin_sz: Int,
                            vout_sz: Int,
                            hash: String,
                            size: Int,
                            time: Long
                          ) {
  def toTransaction = Transaction(inputs.flatMap(_.toInput), out.flatMap(_.toOutput), tx_index, hash, size, time)
  def toFeeOnlyTransaction(height: Long, index: Int, blockTime: Long) = {
    val sumInputs: Long = inputs.flatMap(_.toInput).map(_.value).sum
    val outputs = out.flatMap(_.toOutput).map(_.value)
    val sumOutputs: Long = outputs.sum
    val fees = sumInputs - sumOutputs
    val maxValue = outputs.max
    val maxValueWrapper = MaxValue(maxValue, out.filter(_.value.contains(maxValue)).flatMap(_.hash).headOption.getOrElse(""))
    FeeOnlyTransaction(height, index, fees, maxValueWrapper, size, time, blockTime)
  }
}
