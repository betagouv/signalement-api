package models

import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait CurveTickDuration extends EnumEntry

object CurveTickDuration extends PlayEnum[CurveTickDuration] {

  val values = findValues
  case object Day   extends CurveTickDuration
  case object Week  extends CurveTickDuration
  case object Month extends CurveTickDuration
}
