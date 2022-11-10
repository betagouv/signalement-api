import akka.actor.ActorSystem

import play.api.Logger
import tasks.model.TaskType

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import cats.data.Validated._
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import config.TaskConfiguration
import controllers.error.AppError

import java.util.UUID
import scala.util.Failure
import scala.util.Success
import utils.Logs.RichLogger

package object tasks {

  val logger: Logger = Logger(this.getClass)

  type Task = (UUID, TaskType)
  type TaskExecutionResults = ValidatedNel[Task, List[Task]]
  type TaskExecutionResult = ValidatedNel[Task, Task]

  def toValidated[T](taskExecution: Future[T], elementId: UUID, taskType: TaskType)(implicit
      ec: ExecutionContext
  ): Future[TaskExecutionResult] =
    taskExecution.map(_ => Valid((elementId, taskType))).recover {
      case err: AppError =>
        logger.warn(err.details, err)
        (elementId, taskType).invalidNel[Task]
      case err =>
        val errorMessage = s"Error processing ${taskType.entryName} on element with id : ${elementId}"
        logger.error(errorMessage, err)
        (elementId, taskType).invalidNel[Task]
    }

  def computeStartingTime(startTime: LocalTime): FiniteDuration = {

    val startDate: LocalDateTime =
      if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime)
      else LocalDate.now.atTime(startTime)

    (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds
  }

  def scheduleTask(
      actorSystem: ActorSystem,
      taskConfiguration: TaskConfiguration,
      startTime: LocalTime,
      interval: FiniteDuration,
      taskName: String
  )(execution: => Future[Unit])(implicit e: ExecutionContext): Unit = {
    val initialDelay = computeStartingTime(startTime)
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay,
      interval
    ) { () =>
      if (taskConfiguration.active) {
        logger.infoWithTitle("task_launch", s"$taskName launched")
        execution.onComplete {
          case Success(_) =>
            logger.info(s"$taskName finished")
          case Failure(err) =>
            logger.errorWithTitle("task_failed", s"$taskName failed", err)
        }
      } else logger.info(s"$taskName not launched, tasks are disabled")
      ()
    }
    logger.info(s"$taskName scheduled for $startTime (in $initialDelay)")
    ()
  }

  def getTodayAtStartOfDayParis() =
    OffsetDateTime.now.atZoneSameInstant(ZoneId.of("Europe/Paris")).`with`(LocalTime.MIN).toOffsetDateTime

}
