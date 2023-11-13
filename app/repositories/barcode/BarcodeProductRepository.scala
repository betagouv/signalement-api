package repositories.barcode

import models.barcode.BarcodeProduct
import repositories.CRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BarcodeProductRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[BarcodeProductTable, BarcodeProduct]
    with BarcodeProductRepositoryInterface {

  override val table = BarcodeProductTable.table

  import dbConfig._

  override def getByGTIN(gtin: String): Future[Option[BarcodeProduct]] = db
    .run(
      table
        .filter(_.gtin === gtin)
        .result
        .headOption
    )
}
