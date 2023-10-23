package repositories.gs1

import models.gs1.GS1Product
import repositories.CRUDRepository
import repositories.TypedCRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import scala.concurrent.ExecutionContext

class GS1Repository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[GS1ProductTable, GS1Product, String]
    with GS1RepositoryInterface {

  import dbConfig._

  override val table = GS1ProductTable.table

}
