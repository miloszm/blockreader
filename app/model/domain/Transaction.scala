package model.domain

case class Output(value: Long)

case class Input(value: Long)

case class Transaction(inputs: Seq[Input], outputs: Seq[Output], index: Long, hash: String, size: Int, time: Long) {
  def sumInputs: Long = inputs.map(_.value).sum
  def sumOutputs: Long = outputs.map(_.value).sum
  def fees = sumInputs - sumOutputs
  def numOutputs = outputs.size
  def feePerByte: Long = if (size == 0 || fees < 0) 0 else fees / size
  def age(t: Long): Long = t - time
  def ageInBlocks(t: Long): Long = age(t) / 600
}
