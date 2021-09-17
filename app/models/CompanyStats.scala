package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

trait CompanyReportsCountPeriod {}

case object CompanyReportsCountByDay extends CompanyReportsCountPeriod
case object CompanyReportsCountByWeek extends CompanyReportsCountPeriod
case object CompanyReportsCountByMonth extends CompanyReportsCountPeriod

object CompanyReportsCountPeriod {
  def fromString(period: String): CompanyReportsCountPeriod =
    period match {
      case "day"   => CompanyReportsCountByDay
      case "week"  => CompanyReportsCountByWeek
      case "month" => CompanyReportsCountByMonth
      case _       => CompanyReportsCountByMonth
    }
}

case class ReportReviewStats(
    positive: Int = 0,
    negative: Int = 0
)

object ReportReviewStats {
  implicit val format: OFormat[ReportReviewStats] = Json.format[ReportReviewStats]
}
