package actors

import akka.Done
import akka.actor._
import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl._
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

object UploadActor {
  def props = Props[UploadActor]()

  case class Request(reportFile: ReportFile, file: java.io.File)
}

@Singleton
class UploadActor @Inject() (
    uploadConfiguration: UploadConfiguration,
    reportRepository: ReportRepository,
    s3Service: S3Service
)(implicit
    val mat: Materializer
) extends Actor {
  import UploadActor._

  implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-blocking-dispatcher")

  val avScanEnabled = uploadConfiguration.avScanEnabled

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")

  override def receive = { case Request(reportFile: ReportFile, file: java.io.File) =>
    for {
      _ <- FileIO
        .fromPath(file.toPath)
        .to(s3Service.upload(reportFile.storageFilename))
        .run()
      _ = logger.debug(s"Uploaded file ${reportFile.id} to S3")
      _ <- eventuallyAntivirusScanFile(reportFile, file)
    } yield ()

  }

  private def eventuallyAntivirusScanFile(reportFile: ReportFile, file: java.io.File): Future[Done] =
    for {
      scanOutput <-
        if (avScanEnabled) {
          logger.debug("Begin Antivirus scan.")
          performAntivirusScan(file)
        } else {
          logger.debug("Antivirus scan is not active, skipping scan.")
          Future.successful("Scan is disabled")
        }
      _ <- reportRepository.setAvOutput(reportFile.id, scanOutput)
      noVirusDetected = file.exists()
      _ <-
        if (noVirusDetected) {
          logger.debug("Antivirus scan went fine.")
          Future.successful(file.delete())
        } else {
          logger.warn(s"Antivirus scan found virus, scan output : $scanOutput")
          logger.debug(s"File has been deleted by Antivirus, removing file from S3")
          s3Service.delete(reportFile.storageFilename)
        }
    } yield Done

  private def performAntivirusScan(file: java.io.File): Future[String] = Future {
    val stdout = new StringBuilder
    Seq("clamscan", "--remove", file.toString) ! ProcessLogger(stdout append _)
    logger.debug(stdout.toString)
    stdout.toString()
  }(cpuIntensiveEc)
}

class UploadActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[UploadActor]("upload-actor")
}
