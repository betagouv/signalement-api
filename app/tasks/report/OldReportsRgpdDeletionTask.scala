package tasks.report

import config.TaskConfiguration
import org.apache.pekko.actor.ActorSystem
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings
import controllers.error.AppError.ServerError
import orchestrators.RgpdOrchestrator

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class OldReportsRgpdDeletionTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    rgpdOrchestrator: RgpdOrchestrator,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit val executionContext: ExecutionContext)
    extends ScheduledTask(9, "old_reports_rgpd_deletion_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.oldReportsRgpdDeletion.startTime)

  // The maximum number of reports created in single day so far is 2335 on 2023-05-23
  // So we shouldn't have to delete more than that each day
  private val maxReportsAllowedToDelete = 2500

  override def runTask(): Future[Unit] = {
    val createdBefore = OffsetDateTime.now().minusYears(5)
    for {
      veryOldReports <- reportRepository.getOldReports(createdBefore)
      _ = if (veryOldReports.length > maxReportsAllowedToDelete) {
        // Safety : if we had some date-related bug in this task, it could empty our whole database!
        throw ServerError(
          s"Rgpd deletion failed, it was going to delete ${veryOldReports.length} reports!"
        )
      }
      _ <- veryOldReports.foldLeft(Future.successful(())) { case (previous, report) =>
        for {
          _ <- previous
          _ <- rgpdOrchestrator.deleteRGPD(report)
        } yield ()
      }

    } yield ()
  }

}
