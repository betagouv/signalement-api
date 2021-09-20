package models

import java.time.LocalDate
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportsCountEvolution(
    date: LocalDate,
    reports: Int,
    responses: Int
)

object ReportsCountEvolution {
  implicit val format: OFormat[ReportsCountEvolution] = Json.format[ReportsCountEvolution]
}

case class ReportReviewStats(
    positive: Int = 0,
    negative: Int = 0
)

object ReportReviewStats {
  implicit val format: OFormat[ReportReviewStats] = Json.format[ReportReviewStats]
}
