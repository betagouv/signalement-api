package tasks.account

import org.apache.pekko.actor.ActorSystem
import config.TaskConfiguration
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import utils.Logs.RichLogger

class InactiveAccountTask(
    actorSystem: ActorSystem,
    inactiveDgccrfAccountRemoveTask: InactiveDgccrfAccountRemoveTask,
    inactiveDgccrfAccountSendReminderTask: InactiveDgccrfAccountReminderTask,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(1, "inactive_account_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.inactiveAccounts.startTime)

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
          s"Unexpected failure, cannot run inactive accounts task ( task date : $now, startTime : ${taskSettings.startTime} )",
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
