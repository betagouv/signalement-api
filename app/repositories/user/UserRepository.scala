package repositories.user

import authentication.PasswordHasherRegistry
import controllers.error.AppError.EmailAlreadyExist
import models.UserRole.DGAL
import models.UserRole.DGCCRF
import models._
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import repositories.event.EventTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.Constants.ActionEvent
import utils.Constants.EventType
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

  override def listExpiredAgents(expirationDate: OffsetDateTime): Future[List[User]] =
    db
      .run(
        table
          .filter(user => user.role === DGCCRF.entryName || user.role === DGAL.entryName)
          .filter(_.lastEmailValidation <= expirationDate)
          .to[List]
          .result
      )

  override def listInactiveAgentsWithSentEmailCount(
      reminderDate: OffsetDateTime,
      expirationDate: OffsetDateTime
  ): Future[List[(User, Option[Int])]] =
    db.run(
      UserTable.table
        .filter(user => user.role === DGCCRF.entryName || user.role === DGAL.entryName)
        .filter(_.lastEmailValidation <= reminderDate)
        .filter(_.lastEmailValidation > expirationDate)
        .joinLeft(
          EventTable.table
            .filter(_.action === ActionEvent.EMAIL_INACTIVE_AGENT_ACCOUNT.value)
            .filter(user =>
              user.eventType === EventType.DGCCRF.entryName || user.eventType === EventType.DGAL.entryName
            )
            .filter(_.userId.isDefined)
            .groupBy(_.userId)
            .map { case (userId, results) => userId -> results.length }
        )
        .on(_.id === _._1)
        .map { case (user, event) => user -> event.map(_._2) }
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

  override def findByAuthProviderId(authProviderId: String): Future[Option[User]] =
    db.run(
      table
        .filter(_.authProviderId === authProviderId)
        .result
        .headOption
    )

  override def findByEmailIncludingDeleted(email: String): Future[Option[User]] =
    db.run(
      fullTableIncludingDeleted
        .filter(_.email === EmailAddress(email))
        .sortBy(
          _.deletionDate.desc.nullsFirst
        ) // To ensure fetching an existing account first, to be consistent with findByEmail
        .result
        .headOption
    )

  override def restore(user: User): Future[User] = {
    val restoredUser = user.copy(deletionDate = None)
    db.run(
      fullTableIncludingDeleted.filter(_.id === user.id).update(restoredUser)
    ).map(_ => restoredUser)
  }

  // Override the base method to avoid accidental delete
  override def delete(id: UUID): Future[Int] = softDelete(id)

  override def softDelete(id: UUID): Future[Int] = db.run(
    table
      .filter(_.id === id)
      .map { c =>
        (c.deletionDate, c.password)
      }
      .update((Some(OffsetDateTime.now()), ""))
  )

  override def hardDelete(id: UUID): Future[Int] = db.run(table.filter(_.id === id).delete)

  override def findByEmails(emails: List[EmailAddress]): Future[Seq[User]] =
    db.run(
      table
        .filter(_.email.inSetBind(emails))
        .result
    )

  override def findByIds(ids: Seq[UUID]): Future[Seq[User]] =
    db.run(
      table
        .filter(_.id.inSetBind(ids))
        .result
    )

}
