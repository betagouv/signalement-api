package actors

import actors.antivirus.AntivirusScanExitCode._
import actors.antivirus.AntivirusScanExecution
import actors.antivirus.AntivirusScanExitCode
import akka.Done
import akka.actor.typed._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.google.inject.AbstractModule
import com.google.inject.Provides
import config.UploadConfiguration
import models.report.ReportFile
import play.api.Logger
import play.api.libs.concurrent.ActorModule
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._
import services.S3Service

import java.io.File
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.sys.process._

object AntivirusScanActor extends ActorModule {
  type Message = ScanCommand
  sealed trait ScanCommand
  final case class ScanFromFile(reportFile: ReportFile, file: java.io.File) extends ScanCommand
  final case class ScanFromBucket(reportFile: ReportFile) extends ScanCommand

  val logger: Logger = Logger(this.getClass)

  private def performAntivirusScan(file: java.io.File)(implicit ec: ExecutionContext): Future[AntivirusScanExecution] =
    Future {
      val stdout = new StringBuilder
      val exitCode = Seq("clamdscan", "--remove", "--fdpass", file.toString) ! ProcessLogger(stdout append _)
      AntivirusScanExecution(AntivirusScanExitCode.withValue(exitCode), stdout.toString())
    }

  @Provides
  def create(
      uploadConfiguration: UploadConfiguration,
      reportRepository: ReportRepository,
      s3Service: S3Service
  ): Behavior[ScanCommand] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContextExecutor =
        context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage[ScanCommand] {
        case ScanFromBucket(reportFile: ReportFile) =>
          logger.warn(s"Rescanning file ${reportFile.id} : ${reportFile.storageFilename}")
          for {
            _ <- s3Service.downloadOnCurrentHost(reportFile.storageFilename, reportFile.filename)
            file = new File(reportFile.filename)
          } yield context.self ! ScanFromFile(reportFile, file)
          Behaviors.same

        case ScanFromFile(reportFile: ReportFile, file: java.io.File) =>
          for {
            antivirusScanResult <-
              if (uploadConfiguration.avScanEnabled) {
                logger.debug("Begin Antivirus scan.")
                performAntivirusScan(file)
              } else {
                logger.debug("Antivirus scan is not active, skipping scan.")
                Future.successful(AntivirusScanExecution.Ignored)
              }
            _ = logger.debug(
              s"Saving output to database : ${antivirusScanResult.output}"
            )
            _ <- reportRepository.setAvOutput(reportFile.id, antivirusScanResult.output)
            _ <- antivirusScanResult.exitCode match {
              case Some(NoVirusFound) | None =>
                logger.debug("Deleting file.")
                Future.successful(file.delete())
              case Some(VirusFound) =>
                logger.warn(s"Antivirus scan found virus, scan output : ${antivirusScanResult.output}")
                logger.debug(s"File has been deleted by Antivirus, removing file from S3")
                s3Service
                  .delete(reportFile.storageFilename)
                  .map(_ => reportRepository.removeStorageFileName(reportFile.id))
              case Some(ErrorOccured) =>
                logger.error(s"Unexpected error occured when running scan : ${antivirusScanResult.output}")
                Future.successful(Done)
            }
          } yield Done
          Behaviors.same
      }
    }

}

class AntivirusScanActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindTypedActor(AntivirusScanActor, "antivirus-scan-actor")
}
