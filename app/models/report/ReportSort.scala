package models.report

import enumeratum.EnumEntry
import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.PlayEnum
import models.SortOrder
import models.report.ReportSort.SortCriteria
import utils.QueryStringMapper

case class ReportSort(sortBy: SortCriteria, orderBy: SortOrder)

object ReportSort {
  sealed trait SortCriteria extends EnumEntry with LowerCamelcase

  object SortCriteria extends PlayEnum[SortCriteria] {
    case object CreationDate         extends SortCriteria
    case object SiretByAccount       extends SortCriteria
    case object SiretByPendingReport extends SortCriteria

    override def values: IndexedSeq[SortCriteria] = findValues
  }

  def fromQueryString(q: Map[String, Seq[String]]): Option[ReportSort] = {
    val mapper = new QueryStringMapper(q)
    for {
      sortBy  <- mapper.string("sortBy").flatMap(SortCriteria.withNameOption)
      orderBy <- mapper.string("orderBy").flatMap(SortOrder.withNameOption)
    } yield ReportSort(sortBy, orderBy)
  }
}
