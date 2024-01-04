package orchestrators

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import controllers.HtmlFromTemplateGenerator
import models.report.ReportFile
import play.api.Logger
import services.ZipBuilder.ReportZipEntryName
import services.PDFService
import services.S3ServiceInterface
import services.ZipBuilder

import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportZipExportService(
    htmlFromTemplateGenerator: HtmlFromTemplateGenerator,
    PDFService: PDFService,
    s3Service: S3ServiceInterface
)(implicit
    materializer: Materializer,
    executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  private def getFileExtension(fileName: String): String =
    fileName.lastIndexOf(".") match {
      case -1 => "" // No extension found
      case i  => fileName.substring(i + 1)
    }

  def reportSummaryWithAttachmentsZip(
      reportWithData: ReportWithData
  ): Source[ByteString, Future[IOResult]] = {

    val reportAttachmentSources = reportWithData.files.zipWithIndex.map { case (file, i) =>
      buildReportAttachmentSource(file, i)
    }
    val reportPdfSummarySource = buildReportPdfSummarySource(reportWithData)

    val fileSourcesFutures = reportAttachmentSources :+ reportPdfSummarySource

    ZipBuilder.buildZip(fileSourcesFutures)
  }

  def reportAttachmentsZip(
      reports: Seq[ReportFile]
  ): Source[ByteString, Future[IOResult]] = {

    val reportAttachmentSources = reports.zipWithIndex.map { case (file, i) =>
      buildReportAttachmentSource(file, i + 1)
    }

    ZipBuilder.buildZip(reportAttachmentSources)
  }

  private def buildReportPdfSummarySource(
      reportWithData: ReportWithData
  ): (ReportZipEntryName, Source[ByteString, Unit]) = {
    val htmlForPdf = htmlFromTemplateGenerator.reportPdf(reportWithData)
    (
      ReportZipEntryName(
        s"${reportWithData.report.creationDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}.pdf"
      ),
      PDFService.createPdfSource(Seq(htmlForPdf))
    )
  }

  private def buildReportAttachmentSource(
      reportFile: ReportFile,
      index: Int
  ): (ReportZipEntryName, Source[ByteString, Unit]) = {
    val source = s3Service.downloadFromBucket(reportFile.storageFilename).mapMaterializedValue(_ => ())
    (ReportZipEntryName(s"PJ-$index.${getFileExtension(reportFile.filename)}"), source)
  }

}
