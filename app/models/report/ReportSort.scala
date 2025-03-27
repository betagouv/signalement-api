package models.report

import enumeratum.EnumEntry
import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.PlayEnum
import models.report.ReportSort.SortCriteria
import models.report.ReportSort.SortOrder
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

  sealed trait SortOrder extends EnumEntry with LowerCamelcase

  object SortOrder extends PlayEnum[SortOrder] {
    case object Asc  extends SortOrder
    case object Desc extends SortOrder

    override def values: IndexedSeq[SortOrder] = findValues
  }

  def fromQueryString(q: Map[String, Seq[String]]): Option[ReportSort] = {
    val mapper = new QueryStringMapper(q)
    for {
      sortBy  <- mapper.string("sortBy").flatMap(SortCriteria.withNameOption)
      orderBy <- mapper.string("orderBy").flatMap(SortOrder.withNameOption)
    } yield ReportSort(sortBy, orderBy)
  }
}
