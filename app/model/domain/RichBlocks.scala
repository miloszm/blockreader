package model.domain

import java.time.LocalDateTime

import connectors.BlockchainConnector
import model.StatCalc

case class RichBlocks(blocks: Seq[RichBlock]){
  def totalAvgFeePerByteNoWait: Long = {
    val blcks = blocks.map(_.block).filterNot(_.isEmpty)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks == 0)
    }
    StatCalc.avg(notWaitingTransactions.map(_.feePerByte))
  }
  def last24hMedianFeePerByteNoWait: Long = {
    val now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
    val blcks = blocks.map(_.block).filterNot(_.isEmpty).filter(now - _.time*1000 < 24*3600*1000)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks == 0)
    }
    StatCalc.median(notWaitingTransactions.map(_.feePerByte))
  }
  def last2hMedianFeePerByteNoWait: Long = {
    val now = BlockchainConnector.toEpochMilli(LocalDateTime.now)
    val blcks = blocks.map(_.block).filterNot(_.isEmpty).filter(now - _.time*1000 < 2*3600*1000)
    val notWaitingTransactions = blcks.flatMap { block =>
      block.tx.filter(_.ageInBlocks == 0)
    }
    StatCalc.median(notWaitingTransactions.map(_.feePerByte))
  }
  def totalMedian: Long = {
    val transactions = blocks.map(_.block).flatMap(_.tx)
    StatCalc.median(transactions.map(_.feePerByte))
  }
}
