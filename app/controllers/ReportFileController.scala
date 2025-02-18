package controllers

import org.apache.pekko.Done
import authentication.Authenticator
import cats.implicits.catsSyntaxOption
import config.SignalConsoConfiguration
import controllers.error.AppError.FileNameTooLong
import controllers.error.AppError.FileTooLarge
import controllers.error.AppError.InvalidFileExtension
import controllers.error.AppError.MalformedFileKey
import models.User
import models.report.ReportFile.MaxFileNameLength
import models.report._
import models.report.reportfile.ReportFileId
import orchestrators.ReportFileOrchestrator
import orchestrators.VisibleReportOrchestrator
import play.api.Logger
import play.api.libs.Files
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.MultipartFormData
import utils.DateUtils.frenchFileFormatDate

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportFileController(
    reportFileOrchestrator: ReportFileOrchestrator,
    visibleReportOrchestrator: VisibleReportOrchestrator,
    authenticator: Authenticator[User],
    signalConsoConfiguration: SignalConsoConfiguration,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  val reportFileMaxSizeInBytes = signalConsoConfiguration.reportFileMaxSize * 1024 * 1024

  def downloadFileNotYetUsedInReport(uuid: ReportFileId, filename: String): Action[AnyContent] =
    Act.public.generousLimit.async {
      reportFileOrchestrator
        .downloadFileNotYetUsedInReport(uuid, filename)
        .map(signedUrl => Redirect(signedUrl))
    }

  def downloadFileUsedInReport(uuid: ReportFileId, filename: String): Action[AnyContent] =
    Act.secured.all.allowImpersonation.async { request =>
      reportFileOrchestrator
        .downloadFileUsedInReport(uuid, filename, request.identity)
        .map(signedUrl => Redirect(signedUrl))
    }

  def retrieveReportFiles(): Action[JsValue] = Act.userAware.allowImpersonation.async(parse.json) { request =>
    // Validate the incoming JSON request body against the expected format
    request.body
      .validate[List[ReportFileId]]
      .fold(
        // If validation fails, return a BadRequest with the error details
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        // If validation succeeds, process the list and return the result
        list =>
          reportFileOrchestrator
            .retrieveReportFiles(list)
            .map(files => Ok(Json.toJson(files)))
      )
  }

  def downloadAllFilesAsZip(reportId: UUID, origin: Option[ReportFileOrigin]) =
    Act.secured.all.allowImpersonation.async { request =>
      for {
        reportExtra <- visibleReportOrchestrator.getVisibleReportOrThrow(reportId, request.identity)
        report = reportExtra.report
        stream <- reportFileOrchestrator.downloadReportFilesArchive(report, origin)
      } yield Ok
        .chunked(stream)
        .as("application/zip")
        .withHeaders(
          "Content-Disposition" -> s"attachment; filename=${frenchFileFormatDate(report.creationDate)}.zip"
        )
    }

  def deleteFileUsedInReport(fileId: ReportFileId, filename: String): Action[AnyContent] =
    Act.secured.admins.async {
      reportFileOrchestrator
        .removeFileUsedInReport(fileId, filename)
        .map(_ => NoContent)
    }

  def deleteFileNotYetUsedInReport(fileId: ReportFileId, filename: String): Action[AnyContent] =
    Act.public.standardLimit.async {
      reportFileOrchestrator
        .removeFileNotYetUsedInReport(fileId, filename)
        .map(_ => NoContent)
    }

  def uploadReportFile(reportFileId: Option[UUID]): Action[MultipartFormData[Files.TemporaryFile]] =
    Act.public.generousLimit.async(parse.multipartFormData) { request =>
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
            dataPart,
            reportFileId.map(ReportFileId.apply)
          )
      } yield Ok(Json.toJson(ReportFileApi.buildForFileOwner(reportFile)))
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
