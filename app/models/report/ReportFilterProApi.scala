package models.report

import io.scalaland.chimney.dsl.TransformationOps
import utils.QueryStringMapper

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

case class ReportFilterProApi(
    departments: Seq[String] = Seq.empty,
    fullText: Option[String] = None,
    siretSirenList: Seq[String] = Seq.empty,
    hasCompany: Option[Boolean] = None,
    hasWebsite: Option[Boolean] = None,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None,
    assignedUserId: Option[UUID] = None,

    // Not directly through filters but through screens
    status: Seq[ReportStatus] = Seq.empty
)

object ReportFilterProApi {
  def fromQueryString(q: Map[String, Seq[String]]): Try[ReportFilterProApi] = Try {
    val mapper = new QueryStringMapper(q)
    ReportFilterProApi(
      departments = mapper.seq("departments"),
      fullText = mapper.string("fullText", trimmed = true),
      siretSirenList = mapper.seq("siretSirenList", cleanAllWhitespaces = true),
      hasCompany = mapper.boolean("hasCompany"),
      hasWebsite = mapper.boolean("hasWebsite"),
      // temporary retrocompat, so we can mep the API safely
      start = mapper.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = mapper.timeWithLocalDateRetrocompatEndOfDay("end"),
      status = mapper.seq("status").map(ReportStatus.withName)
    )
  }

  def toReportFilter(reportFilterProApi: ReportFilterProApi): ReportFilter =
    reportFilterProApi
      .into[ReportFilter]
      .enableDefaultValues
      .transform
}
