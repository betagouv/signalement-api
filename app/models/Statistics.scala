package models

import play.api.libs.json.Json
import play.api.libs.json.Writes
import java.time.YearMonth

case class Statistics(
    reportsDurationsForEnvoiSignalement: Int
)

object Statistics {

  implicit val statisticsWrites = new Writes[Statistics] {

    def writes(statistics: Statistics) = Json.obj(
      "reportsDurationsForEnvoiSignalement" -> statistics.reportsDurationsForEnvoiSignalement
    )
  }

}

case class MonthlyStat(
    value: Int,
    yearMonth: YearMonth
)

object MonthlyStat {

  implicit val monthlyStatWrites = new Writes[MonthlyStat] {
    def writes(monthlyCount: MonthlyStat) = Json.obj(
      "month" -> (monthlyCount.yearMonth.getMonthValue - 1),
      "year" -> monthlyCount.yearMonth.getYear,
      "value" -> monthlyCount.value
    )
  }
}
