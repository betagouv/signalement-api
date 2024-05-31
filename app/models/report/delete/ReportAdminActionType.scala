package models.report.delete

import enumeratum._

sealed trait ReportAdminActionType extends EnumEntry

sealed trait ConsumerReportAdminActionType extends ReportAdminActionType
sealed trait ProReportAdminActionType      extends ReportAdminActionType

object ReportAdminActionType extends PlayEnum[ReportAdminActionType] {
  val values = findValues

  case object SolvedContractualDispute extends ConsumerReportAdminActionType
  case object ConsumerThreatenByPro    extends ConsumerReportAdminActionType
  case object RefundBlackMail          extends ConsumerReportAdminActionType
  case object OtherReasonDeleteRequest extends ConsumerReportAdminActionType
  case object SpamDeleteRequest        extends ProReportAdminActionType
}
