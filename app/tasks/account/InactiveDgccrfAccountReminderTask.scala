package tasks.account

import models.User
import models.UserRole
import models.event.Event
import play.api.Logger
import play.api.libs.json.Json
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsDggcrf.DgccrfInactiveAccount
import services.emails.MailService
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Logs.RichLogger

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class InactiveDgccrfAccountReminderTask(
    userRepository: UserRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService
)(implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def sendReminderEmail(
      firstReminderThreshold: OffsetDateTime,
      secondReminderThreshold: OffsetDateTime,
      expirationDateThreshold: OffsetDateTime,
      inactivePeriod: Period
  ): Future[Unit] =
    for {
      firstReminderEvents <- userRepository.listInactiveAgentsWithSentEmailCount(
        firstReminderThreshold,
        expirationDateThreshold
      )
      secondReminderEvents <- userRepository.listInactiveAgentsWithSentEmailCount(
        secondReminderThreshold,
        expirationDateThreshold
      )
      usersToSendFirstReminder  = firstReminderEvents.collect { case (user, None) => user }
      usersToSendSecondReminder = secondReminderEvents.collect { case (user, Some(1)) => user }
      _ <- sendReminderEmailsWithErrorHandling(usersToSendFirstReminder ++ usersToSendSecondReminder, inactivePeriod)
    } yield ()

  private def sendReminderEmail(
      user: User,
      inactivePeriod: Period
  ): Future[Unit] = {
    logger.debug(s"Sending inactive dgccrf account emails")
    val now            = OffsetDateTime.now()
    val expirationDate = user.lastEmailValidation.map(_.plus(inactivePeriod))
    for {
      _ <- mailService.send(DgccrfInactiveAccount.Email(user, expirationDate.map(_.toLocalDate)))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          None,
          None,
          Some(user.id),
          now,
          if (user.userRole == UserRole.DGAL) EventType.DGAL
          else if (user.userRole == UserRole.SSMVM) EventType.SSMVM
          else EventType.DGCCRF,
          ActionEvent.EMAIL_INACTIVE_AGENT_ACCOUNT,
          Json.obj(
            "lastEmailValidation" -> user.lastEmailValidation,
            "expirationDate"      -> expirationDate
          )
        )
      )
    } yield ()
  }

  private def sendReminderEmailsWithErrorHandling(users: List[User], inactivePeriod: Period): Future[Unit] = {
    logger.info(s"Sending inactive dgccrf account reminder emails to ${users.length} users")
    for {
      successesOrFailuresList <- Future.sequence(users.map { user =>
        logger.infoWithTitle("inactive_dgccrf_account_reminder_task_item", s"Inactive user ${user.id}")
        sendReminderEmail(user, inactivePeriod).transform {
          case Success(_) => Success(Right(user.id))
          case Failure(err) =>
            logger.errorWithTitle(
              "inactive_dgccrf_account_reminder_task_item",
              s"Error sending inactive account reminder email to user ${user.id}",
              err
            )
            Success(Left(user.id))
        }
      })
      (failures, successes) = successesOrFailuresList.partitionMap(identity)
      _ = logger.info(s"Successful inactive account reminder emails sent to ${successes.length} users")
      _ = if (failures.nonEmpty)
        logger.error(s"Failed to send inactive account reminder emails to ${failures.length} users")
    } yield ()
  }
}
