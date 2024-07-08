package orchestrators

import cats.implicits.catsSyntaxMonadError
import controllers.error.AppError.EmailAlreadyExist
import controllers.error.AppError.UserNotFound
import models.AccessToken
import models.DraftUser
import models.User
import models.UserRole
import models.UserUpdate
import play.api.Logger
import repositories.user.UserRepositoryInterface
import utils.EmailAddress
import java.time.OffsetDateTime
import cats.syntax.option._
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import repositories.event.EventRepositoryInterface
import utils.Constants.ActionEvent.USER_DELETION
import utils.Constants.EventType.ADMIN

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait UserOrchestratorInterface {
  def createUser(draftUser: DraftUser, accessToken: AccessToken, role: UserRole): Future[User]

  def findOrError(emailAddress: EmailAddress): Future[User]

  def find(emailAddress: EmailAddress): Future[Option[User]]

  def list(emailAddresses: List[EmailAddress]): Future[Seq[User]]

  def edit(userId: UUID, update: UserUpdate): Future[Option[User]]

  def softDelete(targetUserId: UUID, currentUserId: UUID): Future[Unit]

  def updateEmail(user: User, newEmail: EmailAddress): Future[User]
}

class UserOrchestrator(userRepository: UserRepositoryInterface, eventRepository: EventRepositoryInterface)(implicit
    ec: ExecutionContext
) extends UserOrchestratorInterface {
  val logger: Logger = Logger(this.getClass)

  override def edit(id: UUID, update: UserUpdate): Future[Option[User]] =
    for {
      userOpt <- userRepository.get(id)
      updatedUser <- userOpt
        .map(user => userRepository.update(user.id, update.mergeToUser(user)).map(Some(_)))
        .getOrElse(Future.successful(None))
    } yield updatedUser

  def updateEmail(user: User, newEmail: EmailAddress): Future[User] =
    userRepository.update(user.id, user.copy(email = newEmail))

  override def createUser(draftUser: DraftUser, accessToken: AccessToken, role: UserRole): Future[User] = {
    val email: EmailAddress = accessToken.emailedTo.getOrElse(draftUser.email)
    val user = User(
      id = UUID.randomUUID,
      password = draftUser.password,
      email = email,
      firstName = draftUser.firstName,
      lastName = draftUser.lastName,
      userRole = role,
      lastEmailValidation = Some(OffsetDateTime.now())
    )
    for {
      _ <- userRepository.findByEmail(draftUser.email.value).ensure(EmailAlreadyExist)(user => user.isEmpty)
      _ <- userRepository.create(user)
    } yield user
  }

  override def findOrError(emailAddress: EmailAddress): Future[User] =
    userRepository
      .findByEmail(emailAddress.value)
      .flatMap(_.liftTo[Future](UserNotFound(emailAddress.value)))

  override def find(emailAddress: EmailAddress): Future[Option[User]] =
    userRepository
      .findByEmail(emailAddress.value)

  override def softDelete(targetUserId: UUID, currentUserId: UUID): Future[Unit] =
    for {
      _ <- eventRepository.create(
        Event(
          id = UUID.randomUUID(),
          reportId = None,
          companyId = None,
          userId = Some(targetUserId),
          creationDate = OffsetDateTime.now(),
          eventType = ADMIN,
          action = USER_DELETION,
          details = stringToDetailsJsValue(
            s"Suppression manuelle d'un utilisateur par l'admin ${currentUserId}"
          )
        )
      )
      _ = logger.info(s"Soft deleting user ${targetUserId}")
      _ <- userRepository.softDelete(targetUserId).map(_ => ())
    } yield ()

  override def list(emailAddresses: List[EmailAddress]): Future[Seq[User]] =
    userRepository.findByEmails(emailAddresses)
}
