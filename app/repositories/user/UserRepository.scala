package repositories.user

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import controllers.error.AppError.EmailAlreadyExist
import models.UserRole.DGCCRF
import models._
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A repository for user.
  */
class UserRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile],
    passwordHasherRegistry: PasswordHasherRegistry
)(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[UserTable, User]
    with UserRepositoryInterface {

  override val table = UserTable.table
  import UserTable.fullTableIncludingDeleted

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

  override def listForRoles(roles: Seq[UserRole]): Future[Seq[User]] =
    db
      .run(
        table
          .filter(
            _.role.inSetBind(roles.map(_.entryName))
          )
          .result
      )

  override def listDeleted(): Future[Seq[User]] =
    db
      .run(
        fullTableIncludingDeleted.filter(_.deletionDate.nonEmpty).result
      )

  override def create(user: User): Future[User] = {
    val finalUser = user.copy(password = passwordHasherRegistry.current.hash(user.password).password)
    db.run(
      fullTableIncludingDeleted returning fullTableIncludingDeleted += finalUser
    ).map(_ => finalUser)
      .recoverWith {
        case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique") =>
          logger.warn("Cannot create user, provided email already exists")
          Future.failed(EmailAlreadyExist)
      }
  }

  override def updatePassword(userId: UUID, password: String): Future[Int] = {
    val queryUser =
      for (refUser <- table if refUser.id === userId)
        yield refUser
    db.run(
      queryUser
        .map(u => u.password)
        .update(passwordHasherRegistry.current.hash(password).password)
    )
  }

  override def findByEmail(email: String): Future[Option[User]] =
    db.run(
      table
        .filter(_.email === EmailAddress(email))
        .result
        .headOption
    )

  // Override the base method to avoid accidental delete
  override def delete(id: UUID): Future[Int] = softDelete(id)

  override def softDelete(id: UUID): Future[Int] = db.run(
    table.filter(_.id === id).map(_.deletionDate).update(Some(OffsetDateTime.now()))
  )
}
