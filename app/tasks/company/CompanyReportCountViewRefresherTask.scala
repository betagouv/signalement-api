package tasks.company

import config.TaskConfiguration
import org.apache.pekko.actor.ActorSystem
import repositories.companyreportcounts.CompanyReportCountsRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings
import utils.Logs.RichLogger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CompanyReportCountViewRefresherTask(
    actorSystem: ActorSystem,
    repository: CompanyReportCountsRepositoryInterface,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit
    executionContext: ExecutionContext
) extends ScheduledTask(
      13,
      "company_report_count_view_refresher_task",
      taskRepository,
      actorSystem,
      taskConfiguration
    ) {

  override val taskSettings = FrequentTaskSettings(interval = 1.hour)

  override def runTask(): Future[Unit] = {
    logger.infoWithTitle("refresh_view", "BEGIN refresh company_report_counts view")
    repository
      .refreshMaterializeView()
      .map { _ =>
        logger.infoWithTitle("refresh_view", "Successfully refreshed the company_report_counts view")
      }
      .recover { case (e) =>
        logger.errorWithTitle(
          "refresh_view",
          "Failure when refreshed the company_report_counts view",
          e
        )
      }
  }

}
