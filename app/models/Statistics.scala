package models

import java.time.YearMonth

import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class Statistics(
                       reportsCount: Int,
                       reportsPerMonthList: Seq[ReportsPerMonth]
                     )

object Statistics {

  implicit val statisticsWrites = new Writes[Statistics] {
    implicit val reportsPerMonthWrites: Writes[ReportsPerMonth] = ReportsPerMonth.reportsPerMonthWrites
    def writes(statistics: Statistics) = Json.obj(
      "reportsCount" -> statistics.reportsCount,
      "reportsPerMonthList" -> statistics.reportsPerMonthList,
    )
  }

}

case class ReportsPerMonth(
                        count: Int,
                        yearMonth: YearMonth
                      )

object ReportsPerMonth {

  implicit val reportsPerMonthWrites = new Writes[ReportsPerMonth] {
    def writes(reportsPerMonth: ReportsPerMonth) = Json.obj(
      "month" -> (reportsPerMonth.yearMonth.getMonthValue - 1),
      "year" -> reportsPerMonth.yearMonth.getYear,
      "count" -> reportsPerMonth.count
    )
  }

}