package repositories

import java.util.UUID
import java.time.{Duration, OffsetDateTime}

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}
/**
 * A repository for user.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                               passwordHasherRegistry: PasswordHasherRegistry)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._

  class UserTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[UUID]("id", O.PrimaryKey)

    def password = column[String]("password")
    def email = column[EmailAddress]("email")
    def firstName = column[String]("firstname")
    def lastName = column[String]("lastname")
    def role = column[String]("role")
    def lastEmailValidation = column[Option[OffsetDateTime]]("last_email_validation")

    type UserData = (UUID, String, EmailAddress, String, String, String, Option[OffsetDateTime])

    def constructUser: UserData => User = {
      case (id, password, email, firstName, lastName, role, lastEmailValidation) => User(id, password, email, firstName, lastName, UserRoles.withName(role), lastEmailValidation)
    }

    def extractUser: PartialFunction[User, UserData] = {
      case User(id, password, email, firstName, lastName, role, lastEmailValidation) => (id, password, email, firstName, lastName, role.name, lastEmailValidation)
    }

    def * = (id, password, email, firstName, lastName, role, lastEmailValidation) <> (constructUser, extractUser.lift)
  }

  class AuthAttempTable(tag: Tag) extends Table[AuthAttempt](tag, "auth_attempts") {

    def id = column[UUID]("id", O.PrimaryKey)
    def login = column[String]("login")
    def timestamp = column[OffsetDateTime]("timestamp")

    def * = (id, login, timestamp) <> (AuthAttempt.tupled, AuthAttempt.unapply)
  }

  val userTableQuery = TableQuery[UserTable]
  val authAttemptTableQuery = TableQuery[AuthAttempTable]
  
  def list: Future[Seq[User]] = db.run(userTableQuery.result)

  def list(role: UserRole): Future[Seq[User]] =
    db
    .run(userTableQuery
    .filter(_.role === role.name)
    .result)

  def create(user: User): Future[User] = db
    .run(userTableQuery += user.copy(password = passwordHasherRegistry.current.hash(user.password).password))
    .map(_ => user)

  def get(userId: UUID): Future[Option[User]] = db
    .run(userTableQuery.filter(_.id === userId).to[List].result.headOption)

  def countAuthAttempts(login: String, delay: Duration) = db
    .run(
      authAttemptTableQuery
        .filter(_.login === login)
        .filter(_.timestamp >= OffsetDateTime.now.minus(delay))
        .length
        .result
    )

  def saveAuthAttempt(login: String) = db
    .run(
      authAttemptTableQuery += AuthAttempt(UUID.randomUUID, login, OffsetDateTime.now)
    )

  def update(user: User): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === user.id)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.firstName, u.lastName, u.email))
        .update(user.firstName, user.lastName, user.email)
    )
  }

  def updatePassword(userId: UUID, password: String): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === userId)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.password))
        .update(passwordHasherRegistry.current.hash(password).password)
    )
  }

  def delete(userId: UUID): Future[Int] = db
    .run(userTableQuery.filter(_.id === userId).delete)

  def delete(email: EmailAddress): Future[Int] = db
    .run(userTableQuery.filter(_.email === email).delete)

  def findByLogin(login: String): Future[Option[User]] =
    db
    .run(userTableQuery
    .filter(_.email === EmailAddress(login))
    .to[List].result.headOption)
}
