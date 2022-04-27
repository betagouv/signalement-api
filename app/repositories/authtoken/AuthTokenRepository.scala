package repositories.authtoken

import models.auth.AuthToken
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile
import slick.jdbc.JdbcProfile

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
class AuthTokenRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  def create(authToken: AuthToken): Future[AuthToken] = db
    .run(AuthTokenTable.table += authToken)
    .map(_ => authToken)

  def findValid(id: UUID): Future[Option[AuthToken]] = db
    .run(
      AuthTokenTable.table
        .filter(_.id === id)
        .filter(_.expiry > OffsetDateTime.now(ZoneOffset.UTC))
        .to[List]
        .result
        .headOption
    )

  def deleteForUserId(userId: UUID): Future[Int] = db.run {
    AuthTokenTable.table
      .filter(_.userId === userId)
      .delete
  }

  def findForUserId(userId: UUID): Future[Seq[AuthToken]] = db.run {
    AuthTokenTable.table
      .filter(_.userId === userId)
      .result
  }

  def list(): Future[Seq[AuthToken]] = db.run {
    AuthTokenTable.table.result
  }

}
