package actors

import actors.antivirus.AntivirusScanExitCode._
import actors.antivirus.AntivirusScanExecution
import actors.antivirus.AntivirusScanExitCode
import akka.Done
import akka.actor.typed._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import config.UploadConfiguration
import models.report.ReportFile
import play.api.Logger
import repositories.reportfile.ReportFileRepositoryInterface
import services.S3ServiceInterface

import java.io.File
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.sys.process._
import utils.Logs.RichLogger

import scala.util.Failure
import scala.util.Success
object AntivirusScanActor {
  sealed trait ScanCommand
  final case class ScanFromFile(reportFile: ReportFile, file: java.io.File) extends ScanCommand
  final case object ScanFromFileSuccess extends ScanCommand
  final case object ScanFromFileFailed extends ScanCommand
  final case class ScanFromBucket(reportFile: ReportFile) extends ScanCommand
  final case class ScanFromBucketFailed(reportFile: ReportFile) extends ScanCommand

  val logger: Logger = Logger(this.getClass)

  private def performAntivirusScan(file: java.io.File)(implicit ec: ExecutionContext): Future[AntivirusScanExecution] =
    Future {
      val stdout = new StringBuilder()
      val exitCode = Seq("clamdscan", "--remove", "--fdpass", file.toString) ! ProcessLogger(fn = s => {
        stdout.append(s)
        ()
      })
      AntivirusScanExecution(AntivirusScanExitCode.withValue(exitCode), stdout.toString())
    }

  def create(
      uploadConfiguration: UploadConfiguration,
      reportFileRepository: ReportFileRepositoryInterface,
      s3Service: S3ServiceInterface
  ): Behavior[ScanCommand] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContextExecutor =
        context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage[ScanCommand] {
        case ScanFromBucket(reportFile: ReportFile) =>
          logger.warnWithTitle(
            "scan_rescanning_file",
            s"Rescanning file ${reportFile.id.value} : ${reportFile.storageFilename}"
          )
          val filePath = s"${uploadConfiguration.downloadDirectory}/${reportFile.filename}"
          context.pipeToSelf(s3Service.downloadOnCurrentHost(reportFile.storageFilename, filePath)) {
            case Success(_) =>
              val file = new File(filePath)
              ScanFromFile(reportFile, file)
            case Failure(_) =>
              ScanFromBucketFailed(reportFile)
          }
          Behaviors.same

        case ScanFromBucketFailed(reportFile: ReportFile) =>
          logger.warnWithTitle(
            "scan_rescanning_file_failed",
            s"failed to scan from bucket ${reportFile.storageFilename}"
          )
          Behaviors.same

        case ScanFromFile(reportFile: ReportFile, file: java.io.File) =>
          val filePath = s"${uploadConfiguration.downloadDirectory}/${reportFile.filename}"
          val result = for {
            existingFile <- {
              if (file.exists()) {
                Future.successful(file)
              } else {
                s3Service
                  .downloadOnCurrentHost(reportFile.storageFilename, filePath)
                  .map(_ => new File(filePath))
              }
            }
            antivirusScanResult <-
              if (uploadConfiguration.avScanEnabled) {
                logger.debug("Begin Antivirus scan.")
                performAntivirusScan(existingFile)
              } else {
                logger.debug("Antivirus scan is not active, skipping scan.")
                Future.successful(AntivirusScanExecution.Ignored)
              }
            _ = logger.debug(
              s"Saving output to database : ${antivirusScanResult.output}"
            )
            _ <- reportFileRepository.setAvOutput(reportFile.id, antivirusScanResult.output)
            _ <- antivirusScanResult.exitCode match {
              case Some(NoVirusFound) | None =>
                logger.debug("Deleting file.")
                Future.successful(existingFile.delete())
              case Some(VirusFound) =>
                logger.warnWithTitle(
                  "scan_found_virus",
                  s"Antivirus scan found virus, scan output : ${antivirusScanResult.output}"
                )
                logger.debug(s"File has been deleted by Antivirus, removing file from S3")
                s3Service
                  .delete(reportFile.storageFilename)
                  .map(_ => reportFileRepository.removeStorageFileName(reportFile.id))
              case Some(ErrorOccured) =>
                logger.errorWithTitle(
                  "scan_unexpected_error",
                  s"Unexpected error occured when running scan on file $filePath : ${antivirusScanResult.output}"
                )
                Future.successful(Done)
            }
          } yield Done

          context.pipeToSelf(result) {
            case Success(_) => ScanFromFileSuccess
            case Failure(_) => ScanFromFileFailed
          }

          Behaviors.same

        case ScanFromFileSuccess =>
          logger.debug("Scan from file succeeded")
          Behaviors.same
        case ScanFromFileFailed =>
          logger.warnWithTitle("scan_from_file_failed", s"Scan from file failed")
          Behaviors.same
      }
    }

}
