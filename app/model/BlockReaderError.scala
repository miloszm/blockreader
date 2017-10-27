package model

sealed trait BlockReaderError {
  def code:Int
  def message: String
}

case class BlockConnectorError(code:Int, message:String) extends BlockReaderError
