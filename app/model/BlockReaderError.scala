package model

import cats.kernel.Semigroup

final case class BlockReaderError(code: Int, message: String)

object BlockReaderError extends Semigroup[BlockReaderError] {
  override def combine(x: BlockReaderError, y: BlockReaderError) = BlockReaderError(x.code, s"${x.message} ${y.message}")
}
