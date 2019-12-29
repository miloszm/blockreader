package model.domain

import java.time.LocalDateTime

import connectors.BlockchainConnector
import stats.StatCalc

import scala.math.BigDecimal.RoundingMode

case class Transactions(all: Seq[FeeOnlyTransaction]){
  def now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
  val last24h: Seq[FeeOnlyTransaction] = all.filter(now - _.time*1000 < 24*3600*1000)
  val last24h01Blocks = last24h.filter(_.ageInBlocks < 2)
  val last2h: Seq[FeeOnlyTransaction] = all.filter(now - _.time*1000 < 2*3600*1000)
  val last2h01Blocks = last2h.filter(_.ageInBlocks < 2)
  def topBlock: Long = if (last24h.isEmpty) 0L else last24h.map(_.height).max
  def bottomBlock: Long = if (last24h.isEmpty) 0L else last24h.map(_.height).min
  def bottomBlock2h: Long = if (last2h.isEmpty) 0L else last2h.map(_.height).min
  def transactionsLast24h = last24h.size
  def transactionsLast24h01Blocks = last24h01Blocks.size
  def transactionsLast2h = last2h.size
  def transactionsLast2h01Blocks = last2h01Blocks.size
  def totalMedianLast24h: Long = StatCalc.median(last24h.map(_.feePerByte))
  def totalMedianLast24h01Blocks: Long = StatCalc.median(last24h01Blocks.map(_.feePerByte))
  def totalMedianLast2h: Long = StatCalc.median(last2h.map(_.feePerByte))
  def totalMedianLast2h01Blocks: Long = StatCalc.median(last2h01Blocks.map(_.feePerByte))
  def medianLast12Periods2hEach: Seq[(String,Long,String)] = {
    val currentHour = LocalDateTime.now.getHour
    (24 to 2 by -2).map { i =>
      (
        s"${(currentHour + 24 - i) % 24}:00-${(currentHour + 24 - i + 2) % 24}:00",
        StatCalc.median(last24h01Blocks.filter(t => (now - t.time * 1000 < i * 3600 * 1000) && (now - t.time * 1000 > (i - 2) * 3600 * 1000)).map(_.feePerByte)),
        {
          val maxValue = BigDecimal(StatCalc.safeMax(last24h.filter(t => (now - t.time * 1000 < i * 3600 * 1000) && (now - t.time * 1000 > (i - 2) * 3600 * 1000)).map(_.maxValue))) / BigDecimal(100000000)
          if (maxValue.equals(BigDecimal(0))) "0" else maxValue.setScale(8, RoundingMode.FLOOR).toString()
        }
      )
    }
  }
  def feeFor226Bytes: Long = totalMedianLast2h01Blocks * 226
  def feeFor226BytesInBtc: BigDecimal = {
    val btc = BigDecimal(feeFor226Bytes) / BigDecimal(100000000)
    btc.setScale(8, RoundingMode.FLOOR)
  }
  def feeFor226InUsd(usdPrice: BigDecimal): BigDecimal = {
    val priceOfSatoshi = usdPrice / BigDecimal(100000000)
    (BigDecimal(feeFor226Bytes)*priceOfSatoshi).setScale(4, RoundingMode.FLOOR)
  }
  def feeFor1InUsd(usdPrice: BigDecimal): BigDecimal = {
    val priceOfSatoshi = usdPrice / BigDecimal(100000000)
    (BigDecimal(totalMedianLast2h01Blocks)*priceOfSatoshi).setScale(4, RoundingMode.FLOOR)
  }
}

