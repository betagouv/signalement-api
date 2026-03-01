package orchestrators

import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import cats.instances.future._
import controllers.error.AppError
import controllers.error.AppError.AttachmentNotFound
import controllers.error.AppError._
import models._
import models.report._
import models.report.reportfile.ReportFileId
import orchestrators.ReportFileOrchestrator.NotScanned
import orchestrators.ReportFileOrchestrator.ScanStatus
import orchestrators.ReportFileOrchestrator.Scanned
import orchestrators.reportexport.ReportZipExportService
import org.apache.pekko.Done
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
import scala.util.Try
import scala.util.Failure
import scala.util.Success

class ReportFileOrchestrator(
    reportFileRepository: ReportFileRepositoryInterface,
    visibleReportOrchestrator: VisibleReportOrchestrator,
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

  def saveReportFile(
      filename: String,
      file: java.io.File,
      origin: ReportFileOrigin,
      reportFileId: Option[ReportFileId]
  ): Future[ReportFile] =
    for {
      reportFile <- reportFileRepository.create(
        ReportFile(
          reportFileId.getOrElse(ReportFileId.generateId()),
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
    if (antivirusService.isActive) {
      antivirusService.scan(reportFile.id, reportFile.storageFilename).void

    } else {
      Future.unit
    }

  def removeFromReportId(reportId: UUID): Future[List[Int]] =
    for {
      reportFilesToDelete <- reportFileRepository.retrieveReportFiles(reportId)
      res                 <- reportFilesToDelete.map(file => remove(file.id, file.filename)).sequence
    } yield res

  def removeFileUsedInReport(fileId: ReportFileId, filename: String): Future[_] =
    for {
      file <- getFileByIdAndName(fileId, filename)
      _    <- Future.fromTry(checkIsUsedInReport(file))
      _    <- remove(fileId, filename)
    } yield ()

  def removeFileNotYetUsedInReport(fileId: ReportFileId, filename: String): Future[_] =
    for {
      file <- getFileByIdAndName(fileId, filename)
      _    <- Future.fromTry(checkIsNotYetUsedInReport(file))
      _    <- remove(fileId, filename)
    } yield ()

  def legacyDownloadReportAttachment(reportFileId: ReportFileId, filename: String): Future[String] = {
    logger.info(s"Downloading file with id $reportFileId")
    for {
      file <- getReportAttachmentOrRescan(reportFileId, filename)
    } yield s3Service.getSignedUrl(file.storageFilename)
  }

  def downloadFileNotYetUsedInReport(reportFileId: ReportFileId, filename: String): Future[String] = {
    logger.info(s"Downloading file with id $reportFileId")
    for {
      file <- getReportAttachmentOrRescan(reportFileId, filename)
      _    <- Future.fromTry(checkIsNotYetUsedInReport(file))
    } yield s3Service.getSignedUrl(file.storageFilename)
  }

  def downloadFileUsedInReport(
      reportFileId: ReportFileId,
      filename: String,
      maybeUser: Option[User]
  ): Future[String] = {
    logger.info(s"Downloading file with id $reportFileId")
    for {
      file     <- getReportAttachmentOrRescan(reportFileId, filename)
      reportId <- Future.fromTry(checkIsUsedInReport(file))
      _ <- maybeUser match {
        case Some(user) => visibleReportOrchestrator.checkReportIsVisible(reportId, user)
        case _          => Future.unit
      }
    } yield s3Service.getSignedUrl(file.storageFilename)
  }

  private def getReportAttachmentOrRescan(reportFileId: ReportFileId, filename: String): Future[ReportFile] =
    for {
      maybeFile <- reportFileRepository
        .get(reportFileId)
      fileFound       <- maybeFile.liftTo[Future](AttachmentNotFound(reportFileId, filename))
      antivirusResult <- validateAntivirusScanAndRescheduleScanIfNecessary(fileFound)
      fileScanned = antivirusResult match {
        case (Scanned, file)    => file
        case (NotScanned, file) => throw AttachmentNotReady(file.id)
      }
    } yield fileScanned

  private def validateAntivirusScanAndRescheduleScanIfNecessary(
      reportFile: ReportFile
  ): Future[(ScanStatus, ReportFile)] =
    if (reportFile.avOutput.isEmpty && reportFile.reportId.isDefined) {
      logger.info("Attachment has not been scan by antivirus, rescheduling scan")
      if (antivirusService.isActive) {
        antivirusService.fileStatus(reportFile).flatMap {
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
        //Choice is made to not make file available if not scanned
        (NotScanned, reportFile).pure[Future]
      }
    } else {
      Future.successful((Scanned, reportFile))
    }

  def downloadReportFilesArchive(
      report: Report,
      origin: Option[ReportFileOrigin]
  ): Future[Source[ByteString, Future[Done]]] =
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
      _ <- Future.successful(filteredFilesByOrigin).ensure(AppError.NoReportFiles)(_.nonEmpty)
      res = reportZipExportService.reportAttachmentsZip(report, filteredFilesByOrigin)
    } yield res

  private def getFileByIdAndName(fileId: ReportFileId, filename: String) =
    for {
      maybeFile <- reportFileRepository.get(fileId)
      file      <- maybeFile.filter(_.filename == filename).liftTo[Future](AttachmentNotFound(fileId, filename))
    } yield file

  private def remove(fileId: ReportFileId, filename: String): Future[Int] = for {
    res <- reportFileRepository.delete(fileId)
    _   <- s3Service.delete(filename)
  } yield res

  def duplicateIfExist(
      fileId: ReportFileId,
      filename: String,
      maybeReportId: Option[UUID]
  ): Future[Option[ReportFile]] = for {
    file      <- getFileByIdAndName(fileId, filename)
    exist     <- s3Service.exists(file.storageFilename)
    maybeFile <- if (exist) duplicate(file, filename, maybeReportId).map(Some(_)) else Future.successful(None)
  } yield maybeFile

  private def duplicate(file: ReportFile, filename: String, maybeReportId: Option[UUID]): Future[ReportFile] = for {
    data <- s3Service.download(file.storageFilename)
    newName = s"${UUID.randomUUID()}_$filename"
    newFile = ReportFile(
      id = ReportFileId.generateId(),
      reportId = maybeReportId,
      creationDate = OffsetDateTime.now(),
      filename = filename,
      storageFilename = newName,
      origin = file.origin,
      avOutput = file.avOutput
    )
    newReportFile <- reportFileRepository.create(newFile)
    _             <- Source.single(data).runWith(s3Service.upload(newReportFile.storageFilename))
  } yield newReportFile
  private def checkIsNotYetUsedInReport(file: ReportFile): Try[Unit] =
    file.reportId match {
      case None => Success(())
      case Some(_) =>
        logger.warn(s"Cannot act on file ${file.id} this way, because it IS linked to a report")
        Failure(CantPerformAction)
    }

  private def checkIsUsedInReport(file: ReportFile): Try[UUID] =
    file.reportId match {
      case Some(reportId) => Success(reportId)
      case None =>
        logger.warn(s"Cannot act on file ${file.id} this way, because it is NOT linked to a report")
        Failure(CantPerformAction)
    }

}

object ReportFileOrchestrator {
  sealed trait ScanStatus
  case object Scanned    extends ScanStatus
  case object NotScanned extends ScanStatus

}
