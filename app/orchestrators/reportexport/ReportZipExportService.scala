package orchestrators.reportexport

import cats.implicits.catsSyntaxOptionId
import cats.implicits.toTraverseOps
import controllers.HtmlFromTemplateGenerator
import models.User
import models.report.ReportFile
import models.report.ReportFileApi
import orchestrators.ReportWithData
import orchestrators.ReportWithDataOrchestrator
import orchestrators.reportexport.ZipEntryName.AttachmentZipEntryName
import orchestrators.reportexport.ZipEntryName.ReportZipEntryName
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import services.PDFService
import services.S3ServiceInterface
import services.ZipBuilder.buildZip

import java.time.OffsetDateTime
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

  def reportsSummaryZip(reportIds: Seq[UUID], user: User) = {
    val reportFutures = reportIds.traverse(reportWithDataOrchestrator.getReportFull(_, user))
    reportFutures.map { reports =>
      val flattenReports = reports.flatten
      val reportSources =
        flattenReports.map(buildReportPdfSummarySource(_, user, isSingleExport = flattenReports.size > 1))
      buildZip(reportSources)
    }
  }

  def reportSummaryWithAttachmentsZip(
      reportIds: Seq[UUID],
      user: User
  ): Future[Source[ByteString, Future[IOResult]]] = for {
    reportsOptional <- reportIds.traverse(reportWithDataOrchestrator.getReportFull(_, user))
    reports = reportsOptional.flatten
    fileSourcesFutures <- reports.flatTraverse { reportWithData =>
      val reportWithName = buildReportPdfSummarySource(reportWithData, user, isSingleExport = reports.size > 1)
      buildReportAttachmentsSources(
        reportWithData.report.creationDate,
        reportWithData.files,
        reportWithName._1
      ).map(attachments => attachments :+ reportWithName)
    }

  } yield buildZip(fileSourcesFutures)

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
      user: User,
      isSingleExport: Boolean
  ): (ReportZipEntryName, Source[ByteString, Unit]) = {
    val htmlForPdf = htmlFromTemplateGenerator.reportPdf(reportWithData, user)
    (
      ReportZipEntryName(reportWithData, isSingleExport),
      PDFService.createPdfSource(Seq(htmlForPdf))
    )
  }

  private def buildReportAttachmentsSources(
      creationDate: OffsetDateTime,
      reportFiles: Seq[ReportFileApi],
      reportName: ReportZipEntryName
  ): Future[Seq[(ZipEntryName, Source[ByteString, Unit])]] = for {
    existingFiles <- reportFiles
      .traverse(f => s3Service.exists(f.storageFilename).map(exists => (f, exists))) map (_.collect {
      case (file, true) =>
        file
    })
    reportAttachmentSources = existingFiles.zipWithIndex.map { case (file, i) =>
      buildReportAttachmentSource(creationDate, file, i + 1, reportName.some)
    }
  } yield reportAttachmentSources

  private def buildReportAttachmentSource(
      creationDate: OffsetDateTime,
      reportFile: ReportFileApi,
      index: Int,
      reportName: Option[ReportZipEntryName] = None
  ): (ZipEntryName, Source[ByteString, Unit]) = {
    val source = s3Service.downloadFromBucket(reportFile.storageFilename).mapMaterializedValue(_ => ())
    (
      AttachmentZipEntryName(
        reportName,
        reportFile,
        creationDate,
        index
      ),
      source
    )
  }

}
