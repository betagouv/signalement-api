package repositories

import java.time.LocalDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import javax.inject.{Inject, Singleton}
import models.AuthToken
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * A repository for authToken.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class AuthTokenRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                    passwordHasherRegistry: PasswordHasherRegistry)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

 import dbConfig._
 import profile.api._


  private class AuthTokenTable(tag: Tag) extends Table[AuthToken](tag, "auth_tokens") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def expiry = column[LocalDateTime]("expiry")

    type AuthTokenData = (UUID, UUID, LocalDateTime)

    def constructAuthToken: AuthTokenData => AuthToken = {
      case (id, userId, expiry) => AuthToken(id, userId, expiry)
    }

    def extractAuthToken: PartialFunction[AuthToken, AuthTokenData] = {
      case AuthToken(id, userId, expiry) => (id, userId, expiry)
    }

    def * = (id, userId, expiry) <> (constructAuthToken, extractAuthToken.lift)
  }

  private val authTokenTableQuery = TableQuery[AuthTokenTable]

  def create(authToken: AuthToken): Future[AuthToken] = db
    .run(authTokenTableQuery += authToken)
    .map(_ => authToken)

  def findValid(id: UUID): Future[Option[AuthToken]] = db
    .run(authTokenTableQuery
      .filter(_.id === id)
      .filter(_.expiry > LocalDateTime.now)
      .to[List].result.headOption)

  def deleteForUserId(userId: UUID): Future[Int] = db.run {
    authTokenTableQuery
      .filter(_.userId === userId)
      .delete
  }

}
