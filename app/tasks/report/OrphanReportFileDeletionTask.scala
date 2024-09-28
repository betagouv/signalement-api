package tasks.report

import cats.implicits.toFunctorOps
import config.TaskConfiguration
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import repositories.reportfile.ReportFileRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.S3ServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class OrphanReportFileDeletionTask(
    actorSystem: ActorSystem,
    reportFileRepository: ReportFileRepositoryInterface,
    s3Service: S3ServiceInterface,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit val executionContext: ExecutionContext, mat: Materializer)
    extends ScheduledTask(10, "orphan_report_file_deletion_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.orphanReportFileDeletion.startTime)

  override def runTask(): Future[Unit] =
    reportFileRepository.streamOrphanReportFiles
      .mapAsync(parallelism = 1) { r =>
        logger.debug(s"Deleting file ${r.storageFilename}")
        for {
          _ <- s3Service
            .delete(r.storageFilename)
          _ = logger.debug(s"File ${r.storageFilename} deleted from s3")
          _ <- reportFileRepository.delete(r.id)
          _ = logger.debug(s"File ${r.storageFilename} deleted from signal conso database")
        } yield ()
      }
      .recover { case ex =>
        logger.errorWithTitle("orphan_report_file_deletion", "error deleting orphan report file", ex)
      }
      .runWith(Sink.ignore)
      .as(())

}
