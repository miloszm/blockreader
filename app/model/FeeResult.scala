package model

case class FeeResult
(
  topBlock: Long,
  bottomBlock: Long,
  transactionsLast24h: Int,
  medianFeePerByteLast24h: Long,
  medianFeePerByteLast2h: Long,
  last2hPeriods: Seq[(String,Long)],
  feePer226BytesSatoshis: Long,
  feePer226BytesUsd: BigDecimal,
  emptyBlocksExist: Boolean
)

object FeeResult {
  def fromTransactions(all: AllTransactions, emptyExists: Boolean) =
    FeeResult(
      all.topBlock,
      all.bottomBlock,
      all.transactionsLast24h,
      all.totalMedianLast24h,
      all.totalMedianLast2h,
      all.medianLast12Periods2hEach,
      all.feeFor226Bytes,
      all.feeFor226InUsd,
      emptyExists
    )
  def empty = FeeResult(0,0,0,0,0,Nil,0,0,true)
}