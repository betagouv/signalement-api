package models

import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait SortOrder extends EnumEntry with LowerCamelcase

object SortOrder extends PlayEnum[SortOrder] {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder

  override def values: IndexedSeq[SortOrder] = findValues
}
