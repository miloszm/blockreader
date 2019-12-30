package model

import model.domain.Transactions

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
  last2hPeriods: Seq[(String,Long,String,String,String)],
  feePer226BytesSatoshis: Long,
  feePer226BytesBtc: BigDecimal,
  feePer226BytesUsd: BigDecimal,
  feePerByteUsd: BigDecimal,
  emptyBlocksExist: Boolean,
  usdPrice: BigDecimal
)

object FeeResult {
  def fromTransactions(all: Transactions, emptyExists: Boolean, usdPrice: BigDecimal) =
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
      all.feeFor226BytesInBtc,
      all.feeFor226InUsd(usdPrice),
      all.feeFor1InUsd(usdPrice),
      emptyExists,
      usdPrice
    )
  def empty = FeeResult(0,0,0,0,0,0,0,0,0,0,0,Nil,0,0,0,0,true,0)
  def fake = FeeResult(
    550000,
    549900,
    160000,
    150000,
    130,
    140,
    549990,
    17000,
    16500,
    145,
    155,
    Seq(
      ("8:00-10:00", 150, "0", "", ""),
      ("10:00-12:00", 140, "0", "", ""),
      ("12:00-14:00", 130, "0", "", ""),
      ("14:00-16:00", 140, "0", "", ""),
      ("16:00-18:00", 150, "0", "", ""),
      ("18:00-20:00", 140, "0", "", ""),
      ("20:00-22:00", 120, "0", "", ""),
      ("22:00-00:00", 110, "0", "", ""),
      ("00:00-02:00", 120, "0", "", ""),
      ("02:00-04:00", 125, "0", "", ""),
      ("04:00-06:00", 135, "0", "", ""),
      ("06:00-08:00", 150, "0", "", "")
    ),
    155,
    0.00027,
    2.47,
    0.01,
    true,
    8800)
}