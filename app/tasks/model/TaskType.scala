package tasks.model

import enumeratum.EnumEntry
import enumeratum._

sealed trait TaskType extends EnumEntry

object TaskType extends Enum[TaskType] {

  val values = findValues

  case object RemindUnreadReportsByEmail extends TaskType
  case object RemindReadReportByMail extends TaskType
  case object CloseReadReportWithNoAction extends TaskType
  case object CloseUnreadReport extends TaskType

}
