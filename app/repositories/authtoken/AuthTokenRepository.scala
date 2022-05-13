package repositories.authtoken

import models.auth.AuthToken
import play.api.db.slick.DatabaseConfigProvider
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A repository for authToken.
  *
  * @param dbConfigProvider
  *   The Play db config provider. Play will inject this for you.
  */
@Singleton
class AuthTokenRepository @Inject(
    dbConfigProvider: DatabaseConfigProvider
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[AuthTokenTable, AuthToken]
    with AuthTokenRepositoryInterface {

  override val dbConfig = dbConfigProvider.get[JdbcProfile]
  override val table: TableQuery[AuthTokenTable] = AuthTokenTable.table
  import dbConfig._

  override def findValid(id: UUID): Future[Option[AuthToken]] = db
    .run(
      table
        .filter(_.id === id)
        .filter(_.expiry > OffsetDateTime.now(ZoneOffset.UTC))
        .to[List]
        .result
        .headOption
    )

  override def deleteForUserId(userId: UUID): Future[Int] = db.run {
    table
      .filter(_.userId === userId)
      .delete
  }

  override def findForUserId(userId: UUID): Future[Seq[AuthToken]] = db.run {
    table
      .filter(_.userId === userId)
      .result
  }

}
