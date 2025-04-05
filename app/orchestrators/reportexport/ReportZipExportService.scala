package orchestrators.reportexport

import cats.implicits.catsSyntaxOptionId
import controllers.HtmlFromTemplateGenerator
import models.User
import models.report.Report
import models.report.ReportFile
import models.report.ReportFileApi
import models.report.extract.ZipElement
import models.report.extract.ZipElement.ZipReport
import models.report.extract.ZipElement.ZipReportFile
import orchestrators.ReportWithData
import orchestrators.reportexport.ZipEntryName.AttachmentZipEntryName
import orchestrators.reportexport.ZipEntryName.ReportZipEntryName
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import services.PDFService
import services.S3ServiceInterface
import services.ZipBuilder.buildZip

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportZipExportService(
    htmlFromTemplateGenerator: HtmlFromTemplateGenerator,
    pdfService: PDFService,
    s3Service: S3ServiceInterface
)(implicit
    materializer: Materializer,
    ec: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  def reportsSummaryZip(flattenReports: Seq[ReportWithData], user: User) = {

    val reportSources =
      flattenReports.map(buildReportPdfSummarySource(_, user, isSingleExport = flattenReports.size > 1))
    buildZip(reportSources, pdfService.createPdfSource, s3Service.downloadFromBucket, s3Service.exists)

  }

  def reportSummaryWithAttachmentsZip(
      reports: Seq[ReportWithData],
      user: User
  ): Source[ByteString, Future[Done]] = {
    val zipEntries = reports.flatMap { reportWithData =>
      val reportWithName: (ReportZipEntryName, ZipElement) =
        buildReportPdfSummarySource(reportWithData, user, isSingleExport = reports.size > 1)
      val attachments = buildReportAttachmentsSources(
        reportWithData.report.creationDate,
        reportWithData.files,
        reportWithName._1
      )
      attachments :+ reportWithName
    }

    buildZip(
      zipEntries,
      pdfService.createPdfSource,
      s3Service.downloadFromBucket,
      s3Service.exists
    )
  }

  def reportAttachmentsZip(
      report: Report,
      reportFiles: Seq[ReportFile]
  ): Source[ByteString, Future[Done]] = {
    val fileSourcesFutures = buildReportAttachmentsSources(
      report.creationDate,
      reportFiles.map(ReportFileApi.build),
      reportName = ReportZipEntryName(report, true)
    )
    buildZip(fileSourcesFutures, pdfService.createPdfSource, s3Service.downloadFromBucket, s3Service.exists)
  }

  private def buildReportPdfSummarySource(
      reportWithData: ReportWithData,
      user: User,
      isSingleExport: Boolean
  ): (ReportZipEntryName, ZipReport) =
    (
      ReportZipEntryName(reportWithData.report, isSingleExport),
      ZipReport(htmlFromTemplateGenerator.reportPdf(reportWithData, user))
    )

  def getPdfSource(reportWithData: ReportWithData, user: User) =
    pdfService.createPdfSource(Seq(htmlFromTemplateGenerator.reportPdf(reportWithData, user)))

  private def buildReportAttachmentsSources(
      creationDate: OffsetDateTime,
      reportFiles: Seq[ReportFileApi],
      reportName: ReportZipEntryName
  ): Seq[(ZipEntryName, ZipReportFile)] =
    reportFiles.zipWithIndex.map { case (r, i) =>
      buildReportAttachmentSource(creationDate, reportFile = r, index = i + 1, reportName.some)
    }

  private def buildReportAttachmentSource(
      creationDate: OffsetDateTime,
      reportFile: ReportFileApi,
      index: Int,
      reportName: Option[ReportZipEntryName]
  ): (ZipEntryName, ZipReportFile) =
    (
      AttachmentZipEntryName(
        reportName,
        reportFile,
        creationDate,
        index
      ),
      ZipReportFile(reportFile)
    )

}
