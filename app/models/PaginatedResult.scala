package models

import play.api.libs.json.{Json, OFormat, Writes}

case class PaginatedResult[T](
                               totalCount: Int,
                               hasNextPage: Boolean,
                               entities: List[T]
                             )

object PaginatedResult {

  implicit def paginatedReportWriter(implicit userRole: Option[UserRole]) = Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader = Json.reads[PaginatedResult[Report]]

  val paginatedCompanyWithNbReports = Json.writes[PaginatedResult[CompanyWithNbReports]]
}
