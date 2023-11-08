package repositories.barcode

import models.barcode.BarcodeProduct
import play.api.libs.json.JsValue
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime

class BarcodeProductTable(tag: Tag) extends DatabaseTable[BarcodeProduct](tag, "barcode_product") {

  def gtin                 = column[String]("gtin")
  def gs1product           = column[JsValue]("gs1_product")
  def openFoodFactsProduct = column[Option[JsValue]]("open_food_facts_product")
  def creationDate         = column[OffsetDateTime]("creation_date")

  override def * = (
    id,
    gtin,
    gs1product,
    openFoodFactsProduct,
    creationDate
  ) <> ((BarcodeProduct.apply _).tupled, BarcodeProduct.unapply)
}

object BarcodeProductTable {
  val table = TableQuery[BarcodeProductTable]
}
