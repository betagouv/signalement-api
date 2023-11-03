package repositories.gs1

import models.gs1.GS1Product
import play.api.libs.json.JsValue
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime

class GS1ProductTable(tag: Tag) extends DatabaseTable[GS1Product](tag, "gs1_product") {

  def gtin         = column[String]("gtin")
  def product      = column[JsValue]("product")
  def creationDate = column[OffsetDateTime]("creation_date")

  override def * = (
    id,
    gtin,
    product,
    creationDate
  ) <> ((GS1Product.apply _).tupled, GS1Product.unapply)
}

object GS1ProductTable {
  val table = TableQuery[GS1ProductTable]
}
