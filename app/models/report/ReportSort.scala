package models.report

import enumeratum.EnumEntry
import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.PlayEnum
import utils.QueryStringMapper

sealed trait ReportSort extends EnumEntry with LowerCamelcase

object ReportSort extends PlayEnum[ReportSort] {
  case object CreationDate extends ReportSort
  case object Siret        extends ReportSort

  override def values: IndexedSeq[ReportSort] = findValues

  def fromQueryString(q: Map[String, Seq[String]]): Option[ReportSort] = {
    val mapper = new QueryStringMapper(q)
    mapper.string("sortBy").flatMap(ReportSort.withNameOption)
  }
}

sealed trait SortOrder extends EnumEntry with LowerCamelcase

object SortOrder extends PlayEnum[SortOrder] {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder

  override def values: IndexedSeq[SortOrder] = findValues

  def fromQueryString(q: Map[String, Seq[String]]): Option[SortOrder] = {
    val mapper = new QueryStringMapper(q)
    mapper.string("orderBy").flatMap(SortOrder.withNameOption)
  }
}
