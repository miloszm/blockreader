package model

case class FeeResult
(

  topBlock: Long,
  bottomBlock: Long,
  totalTransactions: Int,
  totalMedianFeePerByte: Long,
  medianFeePerByteLast24h: Long,
  medianFeePerByteLast1h: Long,
  last2hPeriods: Seq[Long],
  feePer226BytesSatoshis: Long,
  feePer226BytesUsd: BigDecimal
)

object FeeResult {
  def fromTransactions(all: AllTransactions) =
    FeeResult(
      all.topBlock,
      all.bottomBlock,
      all.all.size,
      all.totalMedian,
      all.totalMedianLast24h,
      all.totalMedianLastHour,
      all.medianLast12Periods2hEach,
      all.feeFor226Bytes,
      all.feeFor226InUsd
    )
  def empty = FeeResult(0,0,0,0,0,0,Nil,0,0)
}