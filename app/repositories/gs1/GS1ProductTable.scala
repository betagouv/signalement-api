package repositories.gs1

import models.gs1.GS1Product
import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import utils.SIREN

import java.time.OffsetDateTime

class GS1ProductTable(tag: Tag) extends DatabaseTable[GS1Product](tag, "gs1_product") {

  def gtin         = column[String]("gtin")
  def siren        = column[SIREN]("siren")
  def creationDate = column[OffsetDateTime]("creation_date")

  override def * = (
    id,
    gtin,
    siren,
    creationDate
  ) <> ((GS1Product.apply _).tupled, GS1Product.unapply)
}

object GS1ProductTable {
  val table = TableQuery[GS1ProductTable]
}
