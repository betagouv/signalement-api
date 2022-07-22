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

sealed trait PublicStat extends EnumEntry {
  val filter: ReportFilter
}
sealed trait PublicStatPercentage extends PublicStat {
  val baseFilter: ReportFilter
}

object PublicStat extends PlayEnum[PublicStat] {
  val values = findValues
  case object PromesseAction extends PublicStat {
    override val filter = ReportFilter(status = Seq(ReportStatus.PromesseAction))
  }
  case object Reports extends PublicStat {
    override val filter = everything
  }
  case object TransmittedPercentage extends PublicStatPercentage {
    override val filter = transmittedFilter
    override val baseFilter = everything
  }
  case object ReadPercentage extends PublicStatPercentage {
    override val filter = ReportFilter(
      status = statusReadByPro
    )
    override val baseFilter = transmittedFilter
  }
  case object ResponsePercentage extends PublicStatPercentage {
    override val filter = ReportFilter(
      status = statusWithProResponse
    )
    override val baseFilter = ReadPercentage.filter
  }
  case object WebsitePercentage extends PublicStatPercentage {
    override val filter = ReportFilter(
      hasWebsite = Some(true)
    )
    override val baseFilter = everything
  }
}
