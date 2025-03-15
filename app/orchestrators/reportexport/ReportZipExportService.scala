package orchestrators.reportexport

import cats.implicits.toTraverseOps
import controllers.HtmlFromTemplateGenerator
import models.User
import models.report.ReportFile
import models.report.ReportFileApi
import orchestrators.ReportWithData
import orchestrators.ReportWithDataOrchestrator
import orchestrators.reportexport.ReportZipExportService.getFileExtension
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import services.PDFService
import services.S3ServiceInterface
import services.ZipBuilder.ReportZipEntryName
import services.ZipBuilder.buildZip

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportZipExportService(
    htmlFromTemplateGenerator: HtmlFromTemplateGenerator,
    PDFService: PDFService,
    s3Service: S3ServiceInterface,
    reportWithDataOrchestrator: ReportWithDataOrchestrator
)(implicit
    materializer: Materializer,
    system: ActorSystem
) {
  val logger: Logger = Logger(this.getClass)

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  def reportsSummaryZip(user: User, reportIds: Seq[UUID]) = {
    val reportFutures = reportIds.traverse(reportWithDataOrchestrator.getReportFull(_, user))
    reportFutures.map { reports =>
      val reportSources = reports.flatten.map(report => buildReportPdfSummarySource(report, user))
      buildZip(reportSources)
    }
  }

  def reportSummaryWithAttachmentsZip(
      reportWithData: ReportWithData,
      user: User
  ): Future[Source[ByteString, Future[IOResult]]] = for {
    reportAttachmentSources <- buildReportAttachmentsSources(
      reportWithData.report.creationDate,
      reportWithData.files
    )
    reportPdfSummarySource = buildReportPdfSummarySource(reportWithData, user)
    fileSourcesFutures     = reportAttachmentSources :+ reportPdfSummarySource
  } yield buildZip(fileSourcesFutures)

  private def buildReportAttachmentsSources(
      creationDate: OffsetDateTime,
      reportFiles: Seq[ReportFileApi]
  ) = for {
    existingFiles <- reportFiles
      .traverse(f => s3Service.exists(f.storageFilename).map(exists => (f, exists))) map (_.collect {
      case (file, true) =>
        file
    })
    reportAttachmentSources = existingFiles.zipWithIndex.map { case (file, i) =>
      buildReportAttachmentSource(creationDate, file, i + 1)
    }
  } yield reportAttachmentSources

  def reportAttachmentsZip(
      creationDate: OffsetDateTime,
      reportFiles: Seq[ReportFile]
  ): Future[Source[ByteString, Future[IOResult]]] = for {
    existingFiles <- reportFiles.traverse(f =>
      s3Service.exists(f.storageFilename).map(exists => (f, exists))
    ) map (_.collect { case (file, true) =>
      file
    })
    reportAttachmentSources = existingFiles.zipWithIndex.map { case (file, i) =>
      buildReportAttachmentSource(creationDate, ReportFileApi.build(file), i + 1)
    }
  } yield buildZip(reportAttachmentSources)

  private def buildReportPdfSummarySource(
      reportWithData: ReportWithData,
      user: User
  ): (ReportZipEntryName, Source[ByteString, Unit]) = {
    val htmlForPdf = htmlFromTemplateGenerator.reportPdf(reportWithData, user)

    (
      ReportZipEntryName(
        s"${reportWithData.report.creationDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}_${reportWithData.report.id}.pdf"
      ),
      PDFService.createPdfSource(Seq(htmlForPdf))
    )
  }

  private def buildReportAttachmentSource(
      creationDate: OffsetDateTime,
      reportFile: ReportFileApi,
      index: Int
  ): (ReportZipEntryName, Source[ByteString, Unit]) = {
    val source = s3Service.downloadFromBucket(reportFile.storageFilename).mapMaterializedValue(_ => ())
    (
      ReportZipEntryName(
        s"${creationDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}-PJ-$index.${getFileExtension(reportFile.filename)}"
      ),
      source
    )
  }

}

object ReportZipExportService {
  private def getFileExtension(fileName: String): String =
    fileName.lastIndexOf(".") match {
      case -1 => "" // No extension found
      case i  => fileName.substring(i + 1)
    }
}
