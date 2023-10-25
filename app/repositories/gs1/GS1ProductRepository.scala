package repositories.gs1

import models.gs1.GS1Product
import repositories.CRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GS1ProductRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[GS1ProductTable, GS1Product]
    with GS1ProductRepositoryInterface {

  import dbConfig._

  override val table = GS1ProductTable.table

  override def getByGTIN(gtin: String): Future[Option[GS1Product]] = db.run(
    table.filter(_.gtin === gtin).result.headOption
  )
}
