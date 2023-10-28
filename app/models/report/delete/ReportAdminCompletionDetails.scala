package models.report.delete

import models.report.ReportTag
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportAdminCompletionDetails(
    category: String,
    subcategories: List[String],
    tags: List[ReportTag],
    comment: String
)

object ReportAdminCompletionDetails {
  implicit val ReportAdminCompletionDetailsFormat: OFormat[ReportAdminCompletionDetails] =
    Json.format[ReportAdminCompletionDetails]
}
