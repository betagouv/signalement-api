package controllers

import org.apache.pekko.Done
import authentication.Authenticator
import cats.implicits.catsSyntaxOption
import config.SignalConsoConfiguration
import controllers.error.AppError
import controllers.error.AppError.FileNameTooLong
import controllers.error.AppError.FileTooLarge
import controllers.error.AppError.InvalidFileExtension
import controllers.error.AppError.MalformedFileKey
import models.User
import models.report.ReportFile.MaxFileNameLength
import models.report._
import models.report.reportfile.ReportFileId
import orchestrators.ReportFileOrchestrator
import play.api.Logger
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.MultipartFormData
import repositories.report.ReportRepositoryInterface
import utils.DateUtils.frenchFileFormatDate

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportFileController(
    reportFileOrchestrator: ReportFileOrchestrator,
    authenticator: Authenticator[User],
    signalConsoConfiguration: SignalConsoConfiguration,
    controllerComponents: ControllerComponents,
    reportRepository: ReportRepositoryInterface
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  val reportFileMaxSizeInBytes = signalConsoConfiguration.reportFileMaxSize * 1024 * 1024

  def downloadReportFile(uuid: ReportFileId, filename: String): Action[AnyContent] = IpRateLimitedAction1.async { _ =>
    reportFileOrchestrator
      .downloadReportAttachment(uuid, filename)
      .map(signedUrl => Redirect(signedUrl))
  }

  def downloadZip(reportId: UUID, origin: Option[ReportFileOrigin]) = IpRateLimitedAction1.async { _ =>
    for {
      report <- reportRepository.get(reportId).flatMap(_.liftTo[Future](AppError.ReportNotFound(reportId)))
      stream <- reportFileOrchestrator.downloadReportFilesArchive(report, origin)
    } yield Ok
      .chunked(stream)
      .as("application/zip")
      .withHeaders(
        "Content-Disposition" -> s"attachment; filename=${frenchFileFormatDate(report.creationDate)}.zip"
      )

  }

  def deleteReportFile(uuid: ReportFileId, filename: String): Action[AnyContent] = UserAwareAction.async {
    implicit request =>
      reportFileOrchestrator
        .removeReportFile(uuid, filename, request.identity)
        .map(_ => NoContent)
  }

  def uploadReportFile: Action[MultipartFormData[Files.TemporaryFile]] =
    IpRateLimitedAction1.async(parse.multipartFormData) { request =>
      for {
        filePart <- request.body.file("reportFile").liftTo[Future](MalformedFileKey("reportFile"))
        _ <-
          if (filePart.filename.length > MaxFileNameLength) {
            Future.failed(FileNameTooLong(MaxFileNameLength, filePart.filename))
          } else Future.unit
        dataPart = request.body.dataParts
          .get("reportFileOrigin")
          .flatMap(o => ReportFileOrigin.withNameInsensitiveOption(o.head))
          .getOrElse(ReportFileOrigin.Consumer)
        fileExtension = filePart.filename.toLowerCase.split("\\.").last
        _ <- validateFileExtension(fileExtension)
        tmpFile = pathFromFilePart(filePart)
        _ <- validateMaxSize(tmpFile)
        reportFile <- reportFileOrchestrator
          .saveReportFile(
            filePart.filename,
            tmpFile,
            dataPart
          )
      } yield Ok(Json.toJson(reportFile))
    }

  private def validateMaxSize(file: File): Future[Unit] =
    if (file.length() > reportFileMaxSizeInBytes) {
      Future.failed(FileTooLarge(reportFileMaxSizeInBytes, file.getName))
    } else {
      Future.unit
    }

  private def pathFromFilePart(filePart: MultipartFormData.FilePart[Files.TemporaryFile]): File = {
    val filename = Paths.get(filePart.filename).getFileName
    val tmpFile =
      new java.io.File(s"${signalConsoConfiguration.tmpDirectory}/${UUID.randomUUID}_${filename}")
    filePart.ref.copyTo(tmpFile): Unit
    tmpFile
  }

  private def validateFileExtension(fileExtension: String): Future[Done.type] = {
    val allowedExtensions: Seq[String] = signalConsoConfiguration.upload.allowedExtensions
    if (allowedExtensions.contains(fileExtension)) Future.successful(Done)
    else Future.failed(InvalidFileExtension(fileExtension, allowedExtensions))
  }

}
