package repositories.blacklistedemails

import models._
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BlacklistedEmailsRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[BlacklistedEmailsTable, BlacklistedEmail]
    with BlacklistedEmailsRepositoryInterface {

  import dbConfig._

  override val table = BlacklistedEmailsTable.table

  override def isBlacklisted(email: String): Future[Boolean] =
    db.run(table.filter(_.email === email).exists.result)

}
