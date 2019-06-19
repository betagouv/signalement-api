package models

import play.api.libs.json.{Json, OFormat, Writes}

case class PaginatedResult[T](
                               totalCount: Int,
                               hasNextPage: Boolean,
                               entities: List[T]
                             )

object PaginatedResult {
  implicit val paginatedReportWriter = Json.writes[PaginatedResult[Report]]
  implicit val paginatedReportReader = Json.reads[PaginatedResult[Report]]

  val paginatedReportProWriter = new Writes[PaginatedResult[Report]] {
    implicit val reportProWriter = Report.reportProWriter
    def writes(paginatedResult: PaginatedResult[Report]) = Json.obj(
      "totalCount" -> paginatedResult.totalCount,
      "hasNextPage" -> paginatedResult.hasNextPage,
      "entities" -> paginatedResult.entities
    )
  }
}
