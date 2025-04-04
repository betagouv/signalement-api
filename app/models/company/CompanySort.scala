package models.company

import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.SortOrder
import models.company.CompanySort.SortCriteria
import utils.QueryStringMapper

case class CompanySort(sortBy: SortCriteria, orderBy: SortOrder)

object CompanySort {
  sealed trait SortCriteria extends EnumEntry with LowerCamelcase

  object SortCriteria extends PlayEnum[SortCriteria] {
    case object ResponseRate extends SortCriteria

    override def values: IndexedSeq[SortCriteria] = findValues
  }

  def fromQueryString(q: Map[String, Seq[String]]): Option[CompanySort] = {
    val mapper = new QueryStringMapper(q)
    for {
      sortBy  <- mapper.string("sortBy").flatMap(SortCriteria.withNameOption)
      orderBy <- mapper.string("orderBy").flatMap(SortOrder.withNameOption)
    } yield CompanySort(sortBy, orderBy)
  }
}
