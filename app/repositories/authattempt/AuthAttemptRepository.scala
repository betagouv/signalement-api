package repositories.authattempt

import models.auth.AuthAttempt
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

@Singleton
class AuthAttemptRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val logger: Logger = Logger(this.getClass)

  import dbConfig._

  def countAuthAttempts(login: String, delay: Duration) = db
    .run(
      AuthAttemptTable.table
        .filter(_.login === login)
        .filter(_.timestamp >= OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(delay.toMinutes))
        .length
        .result
    )

  def listAuthAttempts(login: String) = db
    .run(
      AuthAttemptTable.table
        .filter(_.login === login)
        .result
    )

  def saveAuthAttempt(login: String, isSuccess: Boolean, failureCause: Option[String] = None) = {

    val authAttempt = AuthAttempt(UUID.randomUUID, login, OffsetDateTime.now, Some(isSuccess), failureCause)
    logger.debug(s"Saving auth attempt $authAttempt")
    db
      .run(
        AuthAttemptTable.table += AuthAttempt(UUID.randomUUID, login, OffsetDateTime.now, Some(isSuccess), failureCause)
      )
  }
}
