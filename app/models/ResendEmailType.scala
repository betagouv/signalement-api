package models

import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait ResendEmailType extends EnumEntry

object ResendEmailType extends PlayEnum[ResendEmailType] {
  override def values: IndexedSeq[ResendEmailType] = findValues

  case object NewReportAckToConsumer extends ResendEmailType

  case object NewReportAckToPro extends ResendEmailType

  case object NotifyDGCCRF extends ResendEmailType

  case object ReportProResponse extends ResendEmailType
}
