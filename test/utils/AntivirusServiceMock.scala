package utils

import models.report.reportfile.ReportFileId
import services.antivirus.AntivirusService
import services.antivirus.AntivirusServiceInterface
import services.antivirus.FileData
import services.antivirus.ScanCommand

import java.time.OffsetDateTime
import scala.concurrent.Future

class AntivirusServiceMock extends AntivirusServiceInterface {

  override def scan(
      reportFileId: ReportFileId,
      storageFileName: String
  ): Future[Either[AntivirusService.AntivirusServiceError, Unit]] = Future.successful(Right(()))

  override def reScan(reportFileId: List[ScanCommand]): Future[Either[AntivirusService.AntivirusServiceError, Unit]] =
    Future.successful(Right(()))

  override def fileStatus(
      reportFileId: ReportFileId
  ): Future[Either[AntivirusService.AntivirusServiceError, FileData]] = Future.successful(
    Right(
      FileData(
        reportFileId.value.toString,
        reportFileId.value.toString,
        OffsetDateTime.now(),
        reportFileId.value.toString,
        Some(1),
        avOutput = Some("No virus")
      )
    )
  )

  override def isActive: Boolean = true
}
