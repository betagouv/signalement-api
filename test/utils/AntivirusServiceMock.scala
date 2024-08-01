package utils

import models.report.ReportFile
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
      reportFile: ReportFile
  ): Future[Either[AntivirusService.AntivirusServiceError, FileData]] = Future.successful(
    Right(
      FileData(
        reportFile.id.value.toString,
        reportFile.id.value.toString,
        OffsetDateTime.now(),
        reportFile.id.value.toString,
        Some(1),
        avOutput = Some("No virus")
      )
    )
  )

  override def isActive: Boolean = true

  override def bypassScan: Boolean = false
}
