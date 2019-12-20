package models

import play.api.libs.json.{JsObject, Json, OFormat, Writes}

case class PaginatedResult[T](
                               totalCount: Int,
                               hasNextPage: Boolean,
                               entities: List[T]
                             )

object PaginatedResult {

  implicit def paginatedReportWriter(implicit userRole: Option[UserRole]) = Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader = Json.reads[PaginatedResult[Report]]

  implicit val paginatedReportWithFiles = Json.writes[PaginatedResult[ReportWithFiles]]

  val paginatedCompanyWithNbReports = Json.writes[PaginatedResult[CompanyWithNbReports]]
}
