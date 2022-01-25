package models

import play.api.libs.json.Json
import play.api.libs.json.Writes

case class PaginatedResult[T](
    totalCount: Int,
    hasNextPage: Boolean,
    entities: List[T]
)

object PaginatedResult {

  implicit def paginatedReportWriter(implicit userRole: Option[UserRole]) = Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader = Json.reads[PaginatedResult[Report]]

  def paginatedResultWrites[T](implicit tWrites: Writes[T]) = Json.writes[PaginatedResult[T]]

  implicit def paginatedReportWithFilesWriter(implicit userRole: Option[UserRole]) =
    Json.writes[PaginatedResult[ReportWithFiles]]
}
