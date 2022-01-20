import play.api.Logger

import tasks.model.TaskOutcome.FailedTask
import tasks.model.TaskOutcome.SuccessfulTask
import tasks.model.TaskOutcome
import tasks.model.TaskType

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

package object tasks {

  val logger: Logger = Logger(this.getClass)

  def toTaskOutCome[T](taskExecution: Future[T], reportId: UUID, taskType: TaskType)(implicit
      ec: ExecutionContext
  ): Future[TaskOutcome] =
    taskExecution.map(_ => SuccessfulTask(reportId, taskType)).recover { case err =>
      logger.error(s"Error processing ${taskType.entryName} on report with id : ${reportId}", err)
      FailedTask(reportId, taskType, err)
    }

  def computeStartingTime(startTime: LocalTime) = {

    val startDate: LocalDateTime =
      if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime)
      else LocalDate.now.atTime(startTime)

    (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds
  }

}
