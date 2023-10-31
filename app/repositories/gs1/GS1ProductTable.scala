package repositories.gs1

import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import utils.SIREN

import java.time.OffsetDateTime

class GS1ProductTable(tag: Tag) extends DatabaseTable[GS1ProductRow](tag, "gs1_product") {

  def gtin         = column[String]("gtin")
  def siren        = column[Option[SIREN]]("siren")
  def description  = column[Option[String]]("description")
  def creationDate = column[OffsetDateTime]("creation_date")

  override def * = (
    id,
    gtin,
    siren,
    description,
    creationDate
  ) <> ((GS1ProductRow.apply _).tupled, GS1ProductRow.unapply)
}

object GS1ProductTable {
  val table = TableQuery[GS1ProductTable]
}
