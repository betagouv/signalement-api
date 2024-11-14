package repositories.proconnect

import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A repository for authToken.
  */
class ProConnectSessionRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[ProConnectSessionTable, ProConnectSession]
    with ProConnectSessionRepositoryInterface {

  override val table: TableQuery[ProConnectSessionTable] = ProConnectSessionTable.table
  import dbConfig._

  override def find(state: String): Future[Option[ProConnectSession]] = db
    .run(
      table
        .filter(_.state === state)
        .to[List]
        .result
        .headOption
    )

  override def delete(state: String): Future[Int] = db.run {
    table
      .filter(_.state === state)
      .delete
  }

}
