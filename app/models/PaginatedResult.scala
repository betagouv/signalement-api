package models

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Writes

case class PaginatedResult[T](
    totalCount: Int,
    hasNextPage: Boolean,
    entities: List[T]
)

object PaginatedResult {

  implicit def paginatedReportWriter(implicit userRole: Option[UserRole]) = Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader = Json.reads[PaginatedResult[Report]]

  implicit def paginatedReportWithFilesWriter(implicit userRole: Option[UserRole]) =
    Json.writes[PaginatedResult[ReportWithFiles]]

  val paginatedCompanyWithNbReportsWriter = Json.writes[PaginatedResult[CompanyWithNbReports]]
}
