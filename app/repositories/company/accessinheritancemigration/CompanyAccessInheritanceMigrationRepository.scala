package repositories.company.accessinheritancemigration

import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class CompanyAccessInheritanceMigrationRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[CompanyAccessInheritanceMigrationTable, CompanyAccessInheritanceMigration]
    with CompanyAccessInheritanceMigrationRepositoryInterface {

  override val table: TableQuery[CompanyAccessInheritanceMigrationTable] = CompanyAccessInheritanceMigrationTable.table

}
