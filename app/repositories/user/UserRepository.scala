package repositories.user

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import controllers.error.AppError.EmailAlreadyExist
import models.UserRole.DGCCRF
import models._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A repository for user.
  *
  * @param dbConfigProvider
  *   The Play db config provider. Play will inject this for you.
  */
@Singleton
class UserRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    passwordHasherRegistry: PasswordHasherRegistry
)(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[UserTable, User]
    with UserRepositoryInterface {

  override val dbConfig = dbConfigProvider.get[JdbcProfile]
  override val table: TableQuery[UserTable] = UserTable.table
  val logger: Logger = Logger(this.getClass)

  import dbConfig._

  override def listExpiredDGCCRF(expirationDate: OffsetDateTime): Future[List[User]] =
    db
      .run(
        table
          .filter(_.role === DGCCRF.entryName)
          .filter(_.lastEmailValidation <= expirationDate)
          .to[List]
          .result
      )

  override def list(role: UserRole): Future[Seq[User]] =
    db
      .run(
        table
          .filter(_.role === role.entryName)
          .result
      )

  override def create(user: User): Future[User] =
    super
      .create(user.copy(password = passwordHasherRegistry.current.hash(user.password).password))
      .recoverWith {
        case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique") =>
          logger.warn("Cannot create user, provided email already exists")
          Future.failed(EmailAlreadyExist)
      }

  override def updatePassword(userId: UUID, password: String): Future[Int] = {
    val queryUser =
      for (refUser <- table if refUser.id === userId)
        yield refUser
    db.run(
      queryUser
        .map(u => (u.password))
        .update(passwordHasherRegistry.current.hash(password).password)
    )
  }

  override def list(email: EmailAddress): Future[Seq[User]] = db
    .run(table.filter(_.email === email).result)

  override def delete(email: EmailAddress): Future[Int] = db
    .run(table.filter(_.email === email).delete)

  override def findByLogin(login: String): Future[Option[User]] =
    db.run(
      table
        .filter(_.email === EmailAddress(login))
        .result
        .headOption
    )
}
