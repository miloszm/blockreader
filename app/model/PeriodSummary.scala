package model

import model.domain.MaxValue

case class PeriodSummary(period: String, fee: Long, maxValue: Seq[MaxValue])
