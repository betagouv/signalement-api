package models.report.delete

import enumeratum._

sealed trait ReportAdminActionType extends EnumEntry

object ReportAdminActionType extends PlayEnum[ReportAdminActionType] {
  val values = findValues

  case object SolvedContractualDispute extends ReportAdminActionType
  case object ConsumerThreatenByPro    extends ReportAdminActionType
  case object RefundBlackMail          extends ReportAdminActionType
  case object RGPDDeleteRequest        extends ReportAdminActionType
}
