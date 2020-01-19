package model.json

import model.domain._
import stats.StatCalc

case class JsonOutput(value: Option[Long], addr: Option[String], script: Option[String] = None) {
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
    def containsAddress(addresses: Seq[String], address: Option[String]): Boolean = {
      address match {
        case Some(a) => addresses.contains(a)
        case None => false
      }
    }

    val sumInputs: Long = inputs.flatMap(_.toInput).map(_.value).sum
    val inputAddresses = inputs.flatMap(_.prev_out).flatMap(prevOut => prevOut.addr)
    val outputs = out.flatMap(_.toOutput).map(_.value)
    val outWithoutRest = out.filterNot(o => containsAddress(inputAddresses, o.addr))
    val sumOutputs: Long = outputs.sum
    val fees = sumInputs - sumOutputs
    val maxValueNotHavingInputAddress = StatCalc.safeMax(outWithoutRest.flatMap(_.toOutput).map(_.value))
    val maxValueWrapper = MaxValue(
      maxValueNotHavingInputAddress,
      out.filter(_.value.contains(maxValueNotHavingInputAddress)).flatMap(_.addr).headOption.getOrElse(""),
      out.filter(_.value.contains(maxValueNotHavingInputAddress)).flatMap(_.script).headOption.getOrElse("")
    )
    FeeOnlyTransaction(height, index, fees, maxValueWrapper, size, time, blockTime)
  }
}
