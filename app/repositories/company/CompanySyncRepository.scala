package repositories.company

import models.company.CompanySync
import repositories.CRUDRepository
import repositories.CRUDRepositoryInterface
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

trait CompanySyncRepositoryInterface extends CRUDRepositoryInterface[CompanySync]

class CompanySyncRepository(val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[CompanySyncTable, CompanySync]
    with CompanySyncRepositoryInterface {

  import repositories.PostgresProfile.api._

  val table = TableQuery[CompanySyncTable]

}
