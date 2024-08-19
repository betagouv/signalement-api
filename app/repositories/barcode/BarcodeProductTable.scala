package repositories.barcode

import models.barcode.BarcodeProduct
import play.api.libs.json.JsValue
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime

class BarcodeProductTable(tag: Tag) extends DatabaseTable[BarcodeProduct](tag, "barcode_product") {

  def gtin                   = column[String]("gtin")
  def gs1Product             = column[Option[JsValue]]("gs1_product")
  def openFoodFactsProduct   = column[Option[JsValue]]("open_food_facts_product")
  def openBeautyFactsProduct = column[Option[JsValue]]("open_beauty_facts_product")
  def creationDate           = column[OffsetDateTime]("creation_date")
  def updateDate             = column[OffsetDateTime]("update_date")

  override def * = (
    id,
    gtin,
    gs1Product,
    openFoodFactsProduct,
    openBeautyFactsProduct,
    creationDate,
    updateDate
  ) <> ((BarcodeProduct.apply _).tupled, BarcodeProduct.unapply)
}

object BarcodeProductTable {
  val table = TableQuery[BarcodeProductTable]
}
