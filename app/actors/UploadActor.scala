package actors

import akka.actor._
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
  implicit val ec: ExecutionContext = context.dispatcher

  val avScanEnabled = uploadConfiguration.avScanEnabled

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  override def receive = { case Request(reportFile: ReportFile, file: java.io.File) =>
    if (!avScanEnabled) {
      reportRepository.setAvOutput(reportFile.id, "Scan is disabled")
    }
    if (!avScanEnabled || av_scan(reportFile, file)) {
      FileIO
        .fromPath(file.toPath)
        .to(s3Service.upload(reportFile.storageFilename))
        .run()
        .foreach { _ =>
          logger.debug(s"Uploaded file ${reportFile.id}")
          file.delete()
        }
    } else {
      logger.debug(s"File was deleted (AV scan) ${reportFile.id}")
    }
  }

  def av_scan(reportFile: ReportFile, file: java.io.File) = {
    val stdout = new StringBuilder
    Seq("clamscan", "--remove", file.toString) ! ProcessLogger(stdout append _)
    logger.debug(stdout.toString)
    reportRepository.setAvOutput(reportFile.id, stdout.toString)
    file.exists
  }
}

class UploadActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[UploadActor]("upload-actor")
}
