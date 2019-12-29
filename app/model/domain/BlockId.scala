package model.domain

import org.joda.time.LocalTime

case class BlockId(height: Int, hash: String, time: LocalTime)
