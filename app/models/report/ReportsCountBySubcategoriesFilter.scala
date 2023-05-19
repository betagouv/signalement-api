package models.report

import utils.QueryStringMapper

import java.time.LocalDate
import scala.util.Try

case class ReportsCountBySubcategoriesFilter(
    departments: Seq[String] = Seq.empty,
    start: Option[LocalDate] = None,
    end: Option[LocalDate] = None
)

object ReportsCountBySubcategoriesFilter {
  def fromQueryString(q: Map[String, Seq[String]]): Try[ReportsCountBySubcategoriesFilter] = Try {
    val mapper = new QueryStringMapper(q)
    ReportsCountBySubcategoriesFilter(
      departments = mapper.seq("departments"),
      start = mapper.localDate("start"),
      end = mapper.localDate("end")
    )
  }
}
