package orchestrators

import actors.AntivirusScanActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError
import controllers.error.AppError._
import models._
import models.report._
import models.report.reportfile.ReportFileId
import play.api.Logger
import repositories.reportfile.ReportFileRepositoryInterface
import services.AntivirusServiceInterface
import services.S3ServiceInterface
import services.antivirus.FileData

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportFileOrchestrator(
    reportFileRepository: ReportFileRepositoryInterface,
    antivirusScanActor: ActorRef[AntivirusScanActor.ScanCommand],
    s3Service: S3ServiceInterface,
    reportZipExportService: ReportZipExportService,
    antivirusService: AntivirusServiceInterface
)(implicit val executionContext: ExecutionContext, mat: Materializer) {
  val logger = Logger(this.getClass)

  def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]] =
    reportFileRepository.prefetchReportsFiles(reportsIds)

  def attachFilesToReport(fileIds: List[ReportFileId], reportId: UUID): Future[List[ReportFile]] = for {
    _     <- reportFileRepository.attachFilesToReport(fileIds, reportId)
    files <- reportFileRepository.retrieveReportFiles(reportId)
  } yield files

  def saveReportFile(filename: String, file: java.io.File, origin: ReportFileOrigin): Future[ReportFile] =
    for {
      reportFile <- reportFileRepository.create(
        ReportFile(
          ReportFileId.generateId(),
          reportId = None,
          creationDate = OffsetDateTime.now(),
          filename = filename,
          storageFilename = file.getName(),
          origin = origin,
          avOutput = None
        )
      )
      _ <- FileIO
        .fromPath(file.toPath)
        .to(s3Service.upload(reportFile.storageFilename))
        .run()
      _ = logger.debug(s"Uploaded file ${reportFile.id} to S3")
    } yield {
      requestScan(reportFile, file)
      reportFile
    }

  private def requestScan(reportFile: ReportFile, file: java.io.File) =
    if (antivirusService.isActive) {
      antivirusScanActor ! AntivirusScanActor.ScanFromFile(reportFile, file)
    } else {
      antivirusService.scan(reportFile.id, reportFile.storageFilename)
    }

  def removeFromReportId(reportId: UUID): Future[List[Int]] =
    for {
      reportFilesToDelete <- reportFileRepository.retrieveReportFiles(reportId)
      res                 <- reportFilesToDelete.map(file => remove(file.id, file.filename)).sequence
    } yield res

  def removeReportFile(fileId: ReportFileId, filename: String, user: Option[User]): Future[Int] =
    for {
      maybeReportFile <- reportFileRepository
        .get(fileId)
        .ensure(AttachmentNotFound(reportFileId = fileId, reportFileName = filename))(predicate =
          _.exists(_.filename == filename)
        )
      reportFile <- maybeReportFile.liftTo[Future](AttachmentNotFound(fileId, filename))
      userHasDeleteFilePermission = user.map(_.userRole.permissions).exists(_.contains(UserPermission.deleteFile))
      _ <- reportFile.reportId match {
        case Some(_) if userHasDeleteFilePermission => reportFileRepository.delete(fileId)
        case Some(_) =>
          logger.warn(s"Cannot delete file $fileId because user ${user.map(_.id)} is missing delete file permission")
          Future.failed(CantPerformAction)
        case None => reportFileRepository.delete(fileId)
      }
      res <- remove(fileId, filename)
    } yield res

  private def remove(fileId: ReportFileId, filename: String): Future[Int] = for {
    res <- reportFileRepository.delete(fileId)
    _   <- s3Service.delete(filename)
  } yield res

  def downloadReportAttachment(reportFileId: ReportFileId, filename: String): Future[String] = {
    logger.info(s"Downloading file with id $reportFileId")
    reportFileRepository
      .get(reportFileId)
      .flatMap {
        case Some(reportFile) if reportFile.filename == filename && reportFile.avOutput.isEmpty =>
          rescheduleScan(reportFile).flatMap(_ => Future.successful(s3Service.getSignedUrl(reportFile.storageFilename)))
        case Some(file) if file.filename == filename && file.avOutput.isDefined =>
          Future.successful(s3Service.getSignedUrl(file.storageFilename))
        case _ => Future.failed(AttachmentNotFound(reportFileId, filename))
      }
  }

  private def rescheduleScan(reportFile: ReportFile): Future[String] = {
    logger.info("Attachment has not been scan by antivirus, rescheduling scan")
    if (antivirusService.isActive) {
      antivirusService.fileStatus(reportFile.id).flatMap {
        case Right(FileData(_, _, _, _, Some(0), Some(avOutput))) =>
          reportFileRepository
            .setAvOutput(reportFile.id, avOutput)
            .map(_ => avOutput)
        case _ =>
          antivirusService
            .reScan(List(reportFile.id))
            .flatMap(_ => Future.failed(AttachmentNotReady(reportFile.id)))
      }
    } else {
      antivirusScanActor ! AntivirusScanActor.ScanFromBucket(reportFile)
      Future.failed(AttachmentNotReady(reportFile.id))
    }
  }

  def downloadReportFilesArchive(
      report: Report,
      origin: Option[ReportFileOrigin]
  ): Future[Source[ByteString, Future[IOResult]]] =
    for {
      reportFiles <- reportFileRepository.retrieveReportFiles(report.id)
      filteredFilesByOrigin = reportFiles.filter { f =>
        (origin.contains(f.origin) || origin.isEmpty) && f.avOutput.isDefined
      }
      _   <- Future.successful(filteredFilesByOrigin).ensure(AppError.NoReportFiles)(_.nonEmpty)
      res <- reportZipExportService.reportAttachmentsZip(report.creationDate, filteredFilesByOrigin)
    } yield res

}
