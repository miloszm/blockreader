package model.domain

case class FeeOnlyTransaction(height: Long, index: Int, fees: Long, maxValue: Long, size: Int, time: Long, blockTime: Long){
  def age: Long = blockTime - time
  def ageInBlocks: Long = age / 600
  def feePerByte: Long = if (size == 0 || fees < 0) 0 else fees / size
}

