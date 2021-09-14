package models

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
