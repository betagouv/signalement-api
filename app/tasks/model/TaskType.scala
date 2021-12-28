package tasks.model

import enumeratum.EnumEntry
import enumeratum._

sealed trait TaskType extends EnumEntry

object TaskType extends Enum[TaskType] {

  val values = findValues

  case object RemindOnGoingReportByPost extends TaskType
  case object CloseUnreadReport extends TaskType
  case object RemindReportByMail extends TaskType
  case object CloseTransmittedReportByNoAction extends TaskType

}
