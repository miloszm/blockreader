package model.json

import model.domain.Block


case class JsonBlock(fee: Long, height: Long, n_tx: Int, tx: Seq[JsonTransaction], time: Long) {
  def toBlock = Block(fee, height, n_tx, tx.zipWithIndex.map{case (transaction, index) => transaction.toFeeOnlyTransaction(height, index, time)}, time)
}

