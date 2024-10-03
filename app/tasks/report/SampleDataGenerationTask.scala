package tasks.report

import config.TaskConfiguration
import models.report.sampledata.SampleDataService
import org.apache.pekko.actor.ActorSystem
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SampleDataGenerationTask(
    actorSystem: ActorSystem,
    sampleDataService: SampleDataService,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(8, "sample_data_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.sampleData.startTime)

  override def runTask(): Future[Unit] = sampleDataService.genSampleData()

}
