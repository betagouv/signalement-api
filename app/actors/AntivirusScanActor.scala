package actors

import actors.antivirus.AntivirusScanExitCode._
import actors.antivirus.AntivirusScanExecution
import actors.antivirus.AntivirusScanExitCode
import akka.Done
import akka.actor._
import akka.stream.Materializer
import com.google.inject.AbstractModule
import config.UploadConfiguration
import models.report.ReportFile
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._
import services.S3Service

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.sys.process._

object AntivirusScanActor {
  def props = Props[AntivirusScanActor]()

  case class Request(reportFile: ReportFile, file: java.io.File)
}

@Singleton
class AntivirusScanActor @Inject() (
    uploadConfiguration: UploadConfiguration,
    reportRepository: ReportRepository,
    s3Service: S3Service
)(implicit
    val mat: Materializer
) extends Actor {
  import AntivirusScanActor._

  implicit val ec: ExecutionContext = context.dispatcher

  val avScanEnabled = uploadConfiguration.avScanEnabled

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")

  override def receive = { case Request(reportFile: ReportFile, file: java.io.File) =>
    for {
      antivirusScanResult <-
        if (avScanEnabled) {
          logger.debug("Begin Antivirus scan.")
          performAntivirusScan(file)
        } else {
          logger.debug("Antivirus scan is not active, skipping scan.")
          Future.successful(AntivirusScanExecution.Ignore)
        }
      _ = logger.debug("Saving output.")
      _ <- reportRepository.setAvOutput(reportFile.id, antivirusScanResult.output)
      _ <- antivirusScanResult.exitCode match {
        case Some(NoVirusFound) | None =>
          logger.debug("Deleting file.")
          Future.successful(file.delete())
        case Some(VirusFound) =>
          logger.warn(s"Antivirus scan found virus, scan output : ${antivirusScanResult.output}")
          logger.debug(s"File has been deleted by Antivirus, removing file from S3")
          s3Service.delete(reportFile.storageFilename)
        case Some(ErrorOccured) =>
          logger.error(s"Unexpected error occured when running scan : ${antivirusScanResult.output}")
          Future.successful(Done)
      }
    } yield Done
  }

  private def performAntivirusScan(file: java.io.File): Future[AntivirusScanExecution] = Future {
    val stdout = new StringBuilder
    val exitCode = Seq("clamdscan", "--remove", "--fdpass", file.toString) ! ProcessLogger(stdout append _)
    AntivirusScanExecution(AntivirusScanExitCode.withValue(exitCode), stdout.toString())
  }
}

class UploadActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[AntivirusScanActor](
      "antivirus-scan-actor",
      _.withDispatcher("my-blocking-dispatcher")
    )
}
