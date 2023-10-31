package repositories.gs1

import models.gs1.GS1Product

import java.util.UUID
import scala.concurrent.Future

trait GS1ProductRepositoryInterface {
  def getByGTIN(gtin: String): Future[Option[GS1Product]]
  def create(product: GS1Product): Future[GS1Product]
  def get(id: UUID): Future[Option[GS1Product]]
}
