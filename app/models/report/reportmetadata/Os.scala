package models.report.reportmetadata

import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait Os extends EnumEntry

object Os extends PlayEnum[Os] {
  val values = findValues

  case object Android extends Os
  case object Ios     extends Os
}
