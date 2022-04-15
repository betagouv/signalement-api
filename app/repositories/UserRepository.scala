package repositories

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import controllers.error.AppError.EmailAlreadyExist
import models.UserRole.DGCCRF
import models._
import models.auth.AuthAttempt
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import repositories.user.UserTable
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class AuthAttempTable(tag: Tag) extends Table[AuthAttempt](tag, "auth_attempts") {

  def id = column[UUID]("id", O.PrimaryKey)
  def login = column[String]("login")
  def timestamp = column[OffsetDateTime]("timestamp")
  def isSuccess = column[Option[Boolean]]("is_success")
  def failureCause = column[Option[String]]("failure_cause")

  def * = (id, login, timestamp, isSuccess, failureCause) <> (AuthAttempt.tupled, AuthAttempt.unapply)
}

object AuthAttemptTables {
  val tables = TableQuery[AuthAttempTable]
}

/** A repository for user.
  *
  * @param dbConfigProvider
  *   The Play db config provider. Play will inject this for you.
  */
@Singleton
class UserRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    passwordHasherRegistry: PasswordHasherRegistry
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val logger: Logger = Logger(this.getClass)

  import dbConfig._

  val authAttemptTableQuery = AuthAttemptTables.tables

  def list: Future[Seq[User]] = db.run(UserTable.table.result)

  def listExpiredDGCCRF(expirationDate: OffsetDateTime): Future[List[User]] =
    db
      .run(
        UserTable.table
          .filter(_.role === DGCCRF.entryName)
          .filter(_.lastEmailValidation <= expirationDate)
          .to[List]
          .result
      )

  def list(role: UserRole): Future[Seq[User]] =
    db
      .run(
        UserTable.table
          .filter(_.role === role.entryName)
          .result
      )

  def create(user: User): Future[User] = db
    .run(UserTable.table += user.copy(password = passwordHasherRegistry.current.hash(user.password).password))
    .map(_ => user)
    .recoverWith {
      case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique") =>
        logger.warn("Cannot create user, provided email already exists")
        Future.failed(EmailAlreadyExist)
    }

  def get(userId: UUID): Future[Option[User]] = db
    .run(UserTable.table.filter(_.id === userId).to[List].result.headOption)

  def countAuthAttempts(login: String, delay: Duration) = db
    .run(
      authAttemptTableQuery
        .filter(_.login === login)
        .filter(_.timestamp >= OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(delay.toMinutes))
        .length
        .result
    )

  def listAuthAttempts(login: String) = db
    .run(
      authAttemptTableQuery
        .filter(_.login === login)
        .result
    )

  def saveAuthAttempt(login: String, isSuccess: Boolean, failureCause: Option[String] = None) = {

    val authAttempt = AuthAttempt(UUID.randomUUID, login, OffsetDateTime.now, Some(isSuccess), failureCause)
    logger.debug(s"Saving auth attempt $authAttempt")
    db
      .run(
        authAttemptTableQuery += AuthAttempt(UUID.randomUUID, login, OffsetDateTime.now, Some(isSuccess), failureCause)
      )
  }

  def update(user: User): Future[Int] = {
    val queryUser =
      for (refUser <- UserTable.table if refUser.id === user.id)
        yield refUser
    db.run(
      queryUser
        .map(u => (u.firstName, u.lastName, u.email))
        .update((user.firstName, user.lastName, user.email))
    )
  }

  def updatePassword(userId: UUID, password: String): Future[Int] = {
    val queryUser =
      for (refUser <- UserTable.table if refUser.id === userId)
        yield refUser
    db.run(
      queryUser
        .map(u => (u.password))
        .update(passwordHasherRegistry.current.hash(password).password)
    )
  }

  def delete(userId: UUID): Future[Int] = db
    .run(UserTable.table.filter(_.id === userId).delete)

  def list(email: EmailAddress): Future[Seq[User]] = db
    .run(UserTable.table.filter(_.email === email).result)

  def delete(email: EmailAddress): Future[Int] = db
    .run(UserTable.table.filter(_.email === email).delete)

  def findById(id: UUID): Future[Option[User]] =
    db.run(UserTable.table.filter(_.id === id).result.headOption)

  def findByLogin(login: String): Future[Option[User]] =
    db.run(
      UserTable.table
        .filter(_.email === EmailAddress(login))
        .result
        .headOption
    )
}
