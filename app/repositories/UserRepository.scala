package repositories

import java.util.UUID

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

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

    def login = column[String]("login")
    def password = column[String]("password")
    def activationKey = column[Option[String]]("activation_key")
    def email = column[Option[String]]("email")
    def firstName = column[Option[String]]("firstname")
    def lastName = column[Option[String]]("lastname")
    def role = column[String]("role")

    type UserData = (UUID, String, String, Option[String], Option[String], Option[String], Option[String], String)

    def constructUser: UserData => User = {
      case (id, login, password, activationKey, email, firstName, lastName, role) => User(id, login, password, activationKey, email, firstName, lastName, UserRoles.withName(role))
    }

    def extractUser: PartialFunction[User, UserData] = {
      case User(id, login, password, activationKey, email, firstName, lastName, role) => (id, login, password, activationKey, email, firstName, lastName, role.name)
    }

    def * = (id, login, password, activationKey, email, firstName, lastName, role) <> (constructUser, extractUser.lift)
  }

  val userTableQuery = TableQuery[UserTable]
  
  def list: Future[Seq[User]] = db.run(userTableQuery.result)

  def create(user: User): Future[User] = db
    .run(userTableQuery += user.copy(password = passwordHasherRegistry.current.hash(user.password).password))
    .map(_ => user)

  def get(userId: UUID): Future[Option[User]] = db
    .run(userTableQuery.filter(_.id === userId).to[List].result.headOption)

  def update(user: User): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === user.id)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.firstName, u.lastName, u.email))
        .update(user.firstName, user.lastName, user.email)
    )
  }

  def updateAccountActivation(userId: UUID, activationKey: Option[String], userRole: UserRole): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === userId)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.activationKey, u.role))
        .update(activationKey, userRole.name)
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

  def delete(email: String): Future[Int] = db
    .run(userTableQuery.filter(_.email === email).delete)

  def findByLogin(login: String): Future[Option[User]] = db
    .run(userTableQuery.filter(_.login === login).to[List].result.headOption)

  def prefetchLogins(logins: List[String]): Future[Map[String, User]] = db
    .run(
      userTableQuery
        .filter(_.login inSetBind logins)
        .map(u => (u.login, u))
        .to[List]
        .result
    ).map(_.toMap)
}
