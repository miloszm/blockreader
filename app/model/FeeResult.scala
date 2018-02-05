package model

case class FeeResult
(
  topBlock: Long,
  bottomBlock: Long,
  transactionsLast24h: Int,
  transactionsLast24h01Blocks: Int,
  medianFeePerByteLast24h: Long,
  medianFeePerByteLast24h01Blocks: Long,
  bottomBlock2h: Long,
  transactionsLast2h: Int,
  transactionsLast2h01Blocks: Int,
  medianFeePerByteLast2h: Long,
  medianFeePerByteLast2h01Blocks: Long,
  last2hPeriods: Seq[(String,Long)],
  feePer226BytesSatoshis: Long,
  feePer226BytesUsd: BigDecimal,
  feePerByteUsd: BigDecimal,
  emptyBlocksExist: Boolean,
  usdPrice: BigDecimal
)

object FeeResult {
  def fromTransactions(all: AllTransactions, emptyExists: Boolean, usdPrice: BigDecimal) =
    FeeResult(
      all.topBlock,
      all.bottomBlock,
      all.transactionsLast24h,
      all.transactionsLast24h01Blocks,
      all.totalMedianLast24h,
      all.totalMedianLast24h01Blocks,
      all.bottomBlock2h,
      all.transactionsLast2h,
      all.transactionsLast2h01Blocks,
      all.totalMedianLast2h,
      all.totalMedianLast2h01Blocks,
      all.medianLast12Periods2hEach,
      all.feeFor226Bytes,
      all.feeFor226InUsd(usdPrice),
      all.feeFor1InUsd(usdPrice),
      emptyExists,
      usdPrice
    )
  def empty = FeeResult(0,0,0,0,0,0,0,0,0,0,0,Nil,0,0,0,true,0)
}