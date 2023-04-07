package models.investigation

import enumeratum._

sealed trait InvestigationStatus extends EnumEntry

object InvestigationStatus extends PlayEnum[InvestigationStatus] {

  val values = findValues

  case object NotProcessed extends InvestigationStatus
  case object SignalConsoIdentificationFailed extends InvestigationStatus
  case object Processing extends InvestigationStatus
}
