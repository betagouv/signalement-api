package repositories.barcode

import models.barcode.BarcodeProduct
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait BarcodeProductRepositoryInterface extends CRUDRepositoryInterface[BarcodeProduct] {
  def getByGTIN(gtin: String): Future[Option[BarcodeProduct]]
}
