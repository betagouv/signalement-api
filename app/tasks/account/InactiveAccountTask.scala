package tasks.account

import akka.actor.ActorSystem
import config.TaskConfiguration
import play.api.Logger
import tasks.computeStartingTime

import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class InactiveAccountTask @Inject() (actorSystem: ActorSystem, taskConfiguration: TaskConfiguration)(implicit
    executionContext: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = taskConfiguration.archive.startTime
  val initialDelay: FiniteDuration = computeStartingTime(startTime)

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.day) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime) = {
    logger.info("Removing inactive accounts")
    logger.info(s"taskDate - ${now}")
  }

}
