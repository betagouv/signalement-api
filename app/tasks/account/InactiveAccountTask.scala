package tasks.account

import akka.actor.ActorSystem
import config.TaskConfiguration
import play.api.Logger
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask

import java.time.LocalTime
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import utils.Logs.RichLogger

class InactiveAccountTask(
    actorSystem: ActorSystem,
    inactiveDgccrfAccountRemoveTask: InactiveDgccrfAccountRemoveTask,
    inactiveDgccrfAccountSendReminderTask: InactiveDgccrfAccountReminderTask,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(1, "inactive_account_task", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = taskConfiguration.inactiveAccounts.startTime
  override val interval: FiniteDuration = 1.day

  override def runTask(): Future[Unit] = runTask(OffsetDateTime.now())

  def runTask(now: OffsetDateTime): Future[Unit] = {
    logger.info(s"taskDate - ${now}")
    val expirationDateThreshold: OffsetDateTime = now.minus(taskConfiguration.inactiveAccounts.inactivePeriod)
    val first                                   = now.minus(taskConfiguration.inactiveAccounts.firstReminder)
    val second                                  = now.minus(taskConfiguration.inactiveAccounts.secondReminder)

    val sendReminderEmailsTask = inactiveDgccrfAccountSendReminderTask.sendReminderEmail(
      first,
      second,
      expirationDateThreshold,
      taskConfiguration.inactiveAccounts.inactivePeriod
    )
    val removeInactiveAccountsTask = inactiveDgccrfAccountRemoveTask
      .removeInactiveAccounts(expirationDateThreshold)
      .recoverWith { case err =>
        logger.errorWithTitle(
          "task_remove_inactive_accounts_failed",
          s"Unexpected failure, cannot run inactive accounts task ( task date : $now, startTime : $startTime )",
          err
        )
        Future.failed(err)
      }

    for {
      _ <- sendReminderEmailsTask
      _ <- removeInactiveAccountsTask
    } yield ()

  }
}
