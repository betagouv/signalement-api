package models.report

import utils.QueryStringMapper

import java.time.OffsetDateTime
import scala.util.Try

case class ReportsCountBySubcategoriesFilter(
    departments: Seq[String] = Seq.empty,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None
)

object ReportsCountBySubcategoriesFilter {
  def fromQueryString(q: Map[String, Seq[String]]): Try[ReportsCountBySubcategoriesFilter] = Try {
    val mapper = new QueryStringMapper(q)
    ReportsCountBySubcategoriesFilter(
      departments = mapper.seq("departments"),
      start = mapper.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = mapper.timeWithLocalDateRetrocompatEndOfDay("end")
    )
  }
}
