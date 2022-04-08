package models

import java.time.LocalDate
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class StatsValue(value: Option[Int])

object StatsValue {
  implicit val format: OFormat[StatsValue] = Json.format[StatsValue]
}

case class CountByDate(
    count: Int,
    date: LocalDate
)

object CountByDate {
  implicit val format: OFormat[CountByDate] = Json.format[CountByDate]
}

case class ReportReviewStats(
    positive: Int = 0,
    neutral: Int = 0,
    negative: Int = 0
)

object ReportReviewStats {
  implicit val format: OFormat[ReportReviewStats] = Json.format[ReportReviewStats]
}
