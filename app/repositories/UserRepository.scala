package repositories

import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{User, UserRoles}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * A repository for user.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

 import dbConfig._
 import profile.api._


  private class UserTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[UUID]("id", O.PrimaryKey, O.AutoInc)

    def email = column[String]("email")
    def password = column[String]("password")
    def firstName = column[String]("firstname")
    def lastName = column[String]("lastname")
    def role = column[String]("role")

    type UserData = (Option[UUID], String, String, String, String, String)

    def constructUser: UserData => User = {
      case (id, email, password, firstName, lastName, role) => User(id, email, password, firstName, lastName, UserRoles.withName(role))
    }

    def extractUser: PartialFunction[User, UserData] = {
      case User(id, email, password, firstName, lastName, role) => (id, email, password, firstName, lastName, role.name)
    }

    def * = (id.?, email, password, firstName, lastName, role) <> (constructUser, extractUser.lift)
  }

  private val userTableQuery = TableQuery[UserTable]
  
  def list: Future[Seq[User]] = db.run(userTableQuery.result)

  def create(user: User): Future[User] = db
    .run(userTableQuery returning userTableQuery.map(_.id) += user)
    .map(id => user.copy(id = Some(id)))

  def get(userId: UUID): Future[Option[User]] = db
    .run(userTableQuery.filter(_.id === userId).to[List].result.headOption)

  def update(user: User): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === user.id)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.firstName, u.lastName, u.email, u.role))
        .update(user.firstName, user.lastName, user.email,  user.userRole.name)
    )
  }

  def updatePassword(userId: UUID, password: String): Future[Int] = {
    val queryUser = for (refUser <- userTableQuery if refUser.id === userId)
      yield refUser
    db.run(
      queryUser
        .map(u => (u.password))
        .update(password)
    )
  }

  def delete(userId: UUID): Future[Int] = db
    .run(userTableQuery.filter(_.id === userId).delete)

  def delete(email: String): Future[Int] = db
    .run(userTableQuery.filter(_.email === email).delete)

  def findByEmail(email: String): Future[Option[User]] = db
    .run(userTableQuery.filter(_.email === email).to[List].result.headOption)

}
