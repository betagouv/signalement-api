package models.report.reportmetadata

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportComment(comment: Option[String])

object ReportComment {
  implicit val ReportCommentFormat: OFormat[ReportComment] = Json.format[ReportComment]
}
