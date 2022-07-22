package models

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.report.ReportStatus.statusReadByPro
import models.report.ReportStatus.statusWithProResponse
import models.report.ReportFilter
import models.report.ReportFilter.everything
import models.report.ReportFilter.transmittedFilter
import models.report.ReportStatus

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

case class PublicStat(filter: ReportFilter, percentageBaseFilter: Option[ReportFilter] = None) extends EnumEntry
object MonEnum extends PlayEnum[PublicStat] {
  val values = findValues
  case object PromesseAction extends PublicStat(ReportFilter(status = Seq(ReportStatus.PromesseAction)))
  case object Reports extends PublicStat(everything)
  case object TransmittedPercentage extends PublicStat(transmittedFilter, percentageBaseFilter = Some(everything))
  case object ReadPercentage
      extends PublicStat(
        ReportFilter(status = statusReadByPro),
        percentageBaseFilter = Some(transmittedFilter)
      )
  case object ResponsePercentage
      extends PublicStat(
        ReportFilter(status = statusWithProResponse),
        percentageBaseFilter = Some(ReadPercentage.filter)
      )
  case object WebsitePercentage
      extends PublicStat(
        ReportFilter(hasWebsite = Some(true)),
        percentageBaseFilter = Some(everything)
      )
}
