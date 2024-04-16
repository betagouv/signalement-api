package services.emails

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import utils.EmailAddress

sealed trait EmailCategory extends EnumEntry

object EmailCategory extends PlayEnum[EmailCategory] {
  override def values: IndexedSeq[EmailCategory] = findValues

  case object Various extends EmailCategory

  case object Admin extends EmailCategory

  case object Dgccrf extends EmailCategory
  case object Pro    extends EmailCategory
  case object Conso  extends EmailCategory

}

trait EmailDefinition {
  val category: EmailCategory

  def examples: Seq[(String, EmailAddress => Email)]

}
