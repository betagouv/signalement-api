package tasks.report

import cats.implicits.toFunctorOps
import config.TaskConfiguration
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.S3ServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class OldReportExportDeletionTask(
    actorSystem: ActorSystem,
    reportFileRepository: AsyncFileRepositoryInterface,
    s3Service: S3ServiceInterface,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit val executionContext: ExecutionContext, mat: Materializer)
    extends ScheduledTask(9, "old_report_export_deletion_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.oldReportExportDeletion.startTime)

  override def runTask(): Future[Unit] =
    reportFileRepository.streamOldReportExports
      .mapAsync(parallelism = 1) { r =>
        logger.debug(s"Deleting file ${r.storageFilename}")
        for {
          _ <- r.storageFilename match {
            case Some(storageFilename) =>
              s3Service
                .delete(storageFilename)
            case None => Future.successful(Done)
          }
          _ = logger.debug(s"File ${r.storageFilename} deleted from s3")
//          _ <- reportFileRepository.delete(r.id)
//          _ = logger.debug(s"Export File ${r.storageFilename} deleted from signal conso database")
        } yield ()
      }
      .recover { case ex =>
        logger.errorWithTitle("old_report_export_deletion_task", "error deleting old report export file", ex)
      }
      .runWith(Sink.ignore)
      .as(())

}
