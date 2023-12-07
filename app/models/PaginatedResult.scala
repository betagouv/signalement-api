package models

import models.report.Report
import models.report.ReportWithFiles
import models.report.ReportWithFilesAndResponses
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes

case class PaginatedResult[T](
    totalCount: Int,
    hasNextPage: Boolean,
    entities: List[T]
)

object PaginatedResult {

  implicit def paginatedReportWriter(implicit userRole: Option[UserRole]): OWrites[PaginatedResult[Report]] =
    Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader: Reads[PaginatedResult[Report]] = Json.reads[PaginatedResult[Report]]

  def paginatedResultWrites[T](implicit tWrites: Writes[T]): OWrites[PaginatedResult[T]] =
    Json.writes[PaginatedResult[T]]

  implicit def paginatedReportWithFilesWriter(implicit
      userRole: Option[UserRole]
  ): OWrites[PaginatedResult[ReportWithFiles]] =
    Json.writes[PaginatedResult[ReportWithFiles]]

  implicit def paginatedReportWithFilesAndResponsesWriter(implicit
      userRole: Option[UserRole]
  ): OWrites[PaginatedResult[ReportWithFilesAndResponses]] =
    Json.writes[PaginatedResult[ReportWithFilesAndResponses]]
}
