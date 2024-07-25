package orchestrators

import actors.AntivirusScanActor
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import cats.instances.future._
import controllers.error.AppError
import controllers.error.AppError._
import models._
import models.report._
import models.report.reportfile.ReportFileId
import orchestrators.ReportFileOrchestrator.NotScanned
import orchestrators.ReportFileOrchestrator.ScanStatus
import orchestrators.ReportFileOrchestrator.Scanned
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import repositories.reportfile.ReportFileRepositoryInterface
import services.S3ServiceInterface
import services.antivirus.AntivirusService.NoVirus
import services.antivirus.AntivirusServiceInterface
import services.antivirus.FileData
import services.antivirus.ScanCommand
import utils.Logs.RichLogger

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
    for {
      reportFiles <- reportFileRepository.prefetchReportsFiles(reportsIds)
      allReportFiles = reportFiles.values.flatten.toList
      _ <- allReportFiles.traverse(validateAntivirusScanAndRescheduleScanIfNecessary)
    } yield reportFiles

  def attachFilesToReport(fileIds: List[ReportFileId], reportId: UUID): Future[List[ReportFile]] = for {
    _     <- reportFileRepository.attachFilesToReport(fileIds, reportId)
    files <- reportFileRepository.retrieveReportFiles(reportId)
    _     <- files.traverse(validateAntivirusScanAndRescheduleScanIfNecessary)
  } yield files

  def retrieveReportFiles(fileIds: List[ReportFileId]): Future[List[ReportFileApi]] =
    for {
      files <- reportFileRepository.reportsFiles(fileIds)
      _     <- files.traverse(validateAntivirusScanAndRescheduleScanIfNecessary)
    } yield files.map(ReportFileApi.build)

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
      _ = logger.debug(s"Saving file ${file.getName} to S3")
      _ <- FileIO
        .fromPath(file.toPath)
        .to(
          s3Service
            .upload(reportFile.storageFilename)
        )
        .run()

      _ = logger.debug(s"Uploaded file ${reportFile.id} to S3")
      // Fire and forget scan, if it fails for whatever reason (because external service) the file will be rescanned when user will request it
      _ <- requestScan(reportFile, file)
    } yield reportFile

  private def requestScan(reportFile: ReportFile, file: java.io.File): Future[Unit] =
    if (antivirusService.bypassScan) {
      Future.unit
    } else if (antivirusService.isActive) {
      antivirusService.scan(reportFile.id, reportFile.storageFilename).void

    } else {
      antivirusScanActor ! AntivirusScanActor.ScanFromFile(reportFile, file)
      Future.unit
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
    getReportAttachmentOrRescan(reportFileId, filename).flatMap {
      case Right(reportFile) => Future.successful(s3Service.getSignedUrl(reportFile.storageFilename))
      case Left(error)       => Future.failed(error)
    }
  }

  private def getReportAttachmentOrRescan(reportFileId: ReportFileId, filename: String) =
    reportFileRepository
      .get(reportFileId)
      .flatMap {
        case Some(reportFile) =>
          validateAntivirusScanAndRescheduleScanIfNecessary(reportFile).map {
            case (Scanned, file)    => Right(file)
            case (NotScanned, file) => Left(AttachmentNotReady(file.id): AppError)
          }
        case _ => Future.successful(Left(AttachmentNotFound(reportFileId, filename)))
      }

  private def validateAntivirusScanAndRescheduleScanIfNecessary(
      reportFile: ReportFile
  ): Future[(ScanStatus, ReportFile)] =
    if (antivirusService.bypassScan) {
      Future.successful((Scanned, reportFile))
    } else if (reportFile.avOutput.isEmpty && reportFile.reportId.isDefined) {
      logger.info("Attachment has not been scan by antivirus, rescheduling scan")
      if (antivirusService.isActive) {
        antivirusService.fileStatus(reportFile.id).flatMap {
          case Right(FileData(_, _, _, _, Some(NoVirus), Some(avOutput))) =>
            // Updates AvOutput only when noVirus
            reportFileRepository
              .setAvOutput(reportFile.id, avOutput)
              .map(_ => (Scanned, reportFile.copy(avOutput = Some(avOutput))))
          case _ =>
            antivirusService
              .reScan(List(ScanCommand(reportFile.id.value.toString, reportFile.storageFilename)))
              .map(_ => (NotScanned, reportFile))
        }
      } else {
        antivirusScanActor ! AntivirusScanActor.ScanFromBucket(reportFile)
        (NotScanned, reportFile).pure[Future]
      }
    } else {
      Future.successful((Scanned, reportFile))
    }

  def downloadReportFilesArchive(
      report: Report,
      origin: Option[ReportFileOrigin]
  ): Future[Source[ByteString, Future[IOResult]]] =
    for {
      reportFiles <- reportFileRepository.retrieveReportFiles(report.id)

      errorOrReportFiles <- reportFiles.traverse(validateAntivirusScanAndRescheduleScanIfNecessary).recover { e =>
        logger.warnWithTitle(
          "antivirus_scan_error",
          s"Cannot validate file scan status for files with report : ${report.id} ",
          e
        )
        // Cannot validate file scan status from scan service, proceeding with files that have already been scanned
        reportFiles.collect {
          case file if file.avOutput.nonEmpty => (Scanned, file)
        }
      }
      filteredFilesByOrigin = errorOrReportFiles.collect {
        case (Scanned, value) if origin.contains(value.origin) || origin.isEmpty =>
          value
      }
      _   <- Future.successful(filteredFilesByOrigin).ensure(AppError.NoReportFiles)(_.nonEmpty)
      res <- reportZipExportService.reportAttachmentsZip(report.creationDate, filteredFilesByOrigin)
    } yield res

}

object ReportFileOrchestrator {
  sealed trait ScanStatus
  case object Scanned    extends ScanStatus
  case object NotScanned extends ScanStatus

}
