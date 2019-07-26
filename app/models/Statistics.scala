package models

import java.time.YearMonth

import play.api.libs.json.{Json, Writes}

case class Statistics(
                       reportsCount: Int,
                       reportsPerMonthList: Seq[ReportsPerMonth],
                       reportsCount7Days: Int,
                       reportsCount30Days: Int,
                       reportsCountInRegion: Int,
                       reportsCount7DaysInRegion: Int,
                       reportsCount30DaysInRegion: Int,
                       reportsPercentageSendedToPro: Double,
                       reportsPercentagePromise: Double,
                       reportsPercentageWithoutSiret: Double,
                       reportsCountByCategoryList: Seq[ReportsByCategory],
                       reportsCountByRegionList: Seq[ReportsByRegion],
                       reportsDurationsForEnvoiSignalement: Int
                     )

object Statistics {

  implicit val statisticsWrites = new Writes[Statistics] {
    implicit val reportsPerMonthWrites: Writes[ReportsPerMonth] = ReportsPerMonth.reportsPerMonthWrites
    implicit val reportsByCategoryWrites: Writes[ReportsByCategory] = ReportsByCategory.reportsByCategoryWrites
    implicit val reportsByRegionWrites: Writes[ReportsByRegion] = ReportsByRegion.reportsByRegionWrites

    def writes(statistics: Statistics) = Json.obj(
      "reportsCount" -> statistics.reportsCount,
      "reportsPerMonthList" -> statistics.reportsPerMonthList,
      "reportsCount7Days" -> statistics.reportsCount7Days,
      "reportsCount30Days" -> statistics.reportsCount30Days,
      "reportsCountInRegion" -> statistics.reportsCountInRegion,
      "reportsCount7DaysInRegion" -> statistics.reportsCount7DaysInRegion,
      "reportsCount30DaysInRegion" -> statistics.reportsCount30DaysInRegion,
      "reportsPercentageSendedToPro" -> statistics.reportsPercentageSendedToPro,
      "reportsPercentagePromise" -> statistics.reportsPercentagePromise,
      "reportsPercentageWithoutSiret" -> statistics.reportsPercentageWithoutSiret,
      "reportsCountByCategoryList" -> statistics.reportsCountByCategoryList,
      "reportsCountByRegionList" -> statistics.reportsCountByRegionList,
      "reportsDurationsForEnvoiSignalement" -> statistics.reportsDurationsForEnvoiSignalement
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

case class ReportsByCategory(category: String, count: Int)

object ReportsByCategory {

  implicit val reportsByCategoryWrites = new Writes[ReportsByCategory] {
    def writes(reportsByCategory: ReportsByCategory) = Json.obj(
      "category" -> reportsByCategory.category,
      "count" -> reportsByCategory.count
    )
  }
}

case class ReportsByRegion(region: String, count: Int)

object ReportsByRegion {

  implicit val reportsByRegionWrites = new Writes[ReportsByRegion] {
    def writes(reportsByRegion: ReportsByRegion) = Json.obj(
      "region" -> reportsByRegion.region,
      "count" -> reportsByRegion.count
    )
  }
}

