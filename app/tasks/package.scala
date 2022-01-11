import play.api.Logger

import tasks.model.TaskOutcome.FailedTask
import tasks.model.TaskOutcome.SuccessfulTask
import tasks.model.TaskOutcome
import tasks.model.TaskType

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

package object tasks {

  val logger: Logger = Logger(this.getClass)

  def toTaskOutCome[T](taskExecution: Future[T], reportId: UUID, taskType: TaskType)(implicit
      ec: ExecutionContext
  ): Future[TaskOutcome] =
    taskExecution.map(_ => SuccessfulTask(reportId, taskType)).recover { case err =>
      logger.error(s"Error processing ${taskType.entryName} on report with id : ${reportId}", err)
      FailedTask(reportId, taskType, err)
    }

}
