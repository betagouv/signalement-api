package models.report.delete

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportAdminAction(reportAdminActionType: ReportAdminActionType, comment: Option[String])

object ReportAdminAction {
  implicit val ReportDeletionReasonFormat: OFormat[ReportAdminAction] = Json.format[ReportAdminAction]
}
