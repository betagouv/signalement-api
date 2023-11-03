package repositories.gs1

import models.gs1.GS1Product
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait GS1ProductRepositoryInterface extends CRUDRepositoryInterface[GS1Product] {
  def getByGTIN(gtin: String): Future[Option[GS1Product]]
}
