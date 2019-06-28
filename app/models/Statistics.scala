package models

import java.time.YearMonth

import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class Statistics(
                       reportsCount: Int,
                       reportsPerMonthList: Seq[ReportsPerMonth],
                       reportsCount7Days: Int,
                       reportsCount30Days: Int,
                       reportsCount7DaysInRegion: Int,
                       reportsCount30DaysInRegion: Int,
                       reportsCountSendedToPro: Int,
                       reportsCountPromise: Int
                     )

object Statistics {

  implicit val statisticsWrites = new Writes[Statistics] {
    implicit val reportsPerMonthWrites: Writes[ReportsPerMonth] = ReportsPerMonth.reportsPerMonthWrites
    def writes(statistics: Statistics) = Json.obj(
      "reportsCount" -> statistics.reportsCount,
      "reportsPerMonthList" -> statistics.reportsPerMonthList,
      "reportsCount7Days" -> statistics.reportsCount7Days,
      "reportsCount30Days" -> statistics.reportsCount30Days,
      "reportsCount7DaysInRegion" -> statistics.reportsCount7Days,
      "reportsCount30DaysInRegion" -> statistics.reportsCount30Days,
      "reportsCountSendedToPro" -> statistics.reportsCountSendedToPro,
      "reportsCountPromise" -> statistics.reportsCountPromise
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

case class NumberSignalement (count: Int)

object NumberSignalement {

  implicit val numberSignalementWrites = new Writes[NumberSignalement] {
    def writes(numberSignalement: NumberSignalement) = Json.obj(
      "count" -> numberSignalement.count
    )
  }
}
