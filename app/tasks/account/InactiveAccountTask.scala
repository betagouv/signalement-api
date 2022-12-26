package tasks.account

import akka.actor.ActorSystem
import config.TaskConfiguration
import play.api.Logger
import tasks.computeStartingTime

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import utils.Logs.RichLogger

class InactiveAccountTask(
    actorSystem: ActorSystem,
    inactiveDgccrfAccountRemoveTask: InactiveDgccrfAccountRemoveTask,
    taskConfiguration: TaskConfiguration
)(implicit
    executionContext: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = taskConfiguration.inactiveAccounts.startTime
  val initialDelay: FiniteDuration = computeStartingTime(startTime)

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.day) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    if (taskConfiguration.active) {
      runTask(
        OffsetDateTime.now()
      )
    }
    ()
  }

  def runTask(now: OffsetDateTime) = {
    logger.info(s"taskDate - ${now}")
    val expirationDateThreshold: OffsetDateTime = now.minus(taskConfiguration.inactiveAccounts.inactivePeriod)

    inactiveDgccrfAccountRemoveTask.removeInactiveAccounts(expirationDateThreshold).recoverWith { case err =>
      logger.errorWithTitle(
        "task_remove_inactive_accounts_failed",
        s"Unexpected failure, cannot run inactive accounts task ( task date : $now, initialDelay : $initialDelay )",
        err
      )
      Future.failed(err)
    }
  }
}
