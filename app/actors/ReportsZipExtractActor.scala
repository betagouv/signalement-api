package actors

import models.UserRole.Professionnel
import models._
import models.report._
import orchestrators.ReportWithDataOrchestrator
import orchestrators.reportexport.ReportZipExportService
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.StashBuffer
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface
import services.S3ServiceInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Failure
import scala.util.Success

object ReportsZipExtractActor {

  final val logger = Logger(getClass)

  sealed trait ReportsExtractCommand

  case class ExtractRequest(fileId: UUID, requestedBy: User, filters: ReportFilter) extends ReportsExtractCommand

  case class ExtractRequestSuccess(fileId: UUID, requestedBy: User) extends ReportsExtractCommand

  case class ExtractRequestFailure(error: Throwable) extends ReportsExtractCommand

  def create(
      reportOrchestrator: ReportWithDataOrchestrator,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      reportZipExportService: ReportZipExportService
  ): Behavior[ReportsExtractCommand] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup { context =>
        idle(
          reportOrchestrator,
          asyncFileRepository,
          s3Service,
          reportZipExportService,
          buffer,
          context
        )
      }
    }

  private def idle(
      reportOrchestrator: ReportWithDataOrchestrator,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      reportZipExportService: ReportZipExportService,
      buffer: StashBuffer[ReportsExtractCommand],
      context: ActorContext[ReportsExtractCommand]
  ): Behavior[ReportsExtractCommand] =
    Behaviors.receiveMessage {
      case req: ExtractRequest =>
        context.pipeToSelf(
          handleExtractRequest(
            reportOrchestrator,
            asyncFileRepository,
            s3Service,
            reportZipExportService,
            req,
            context
          )
        ) {
          case Success(success) => success
          case Failure(error)   => ExtractRequestFailure(error)
        }
        busy(
          reportOrchestrator,
          asyncFileRepository,
          s3Service,
          reportZipExportService,
          buffer,
          context
        )
      case _ =>
        // Ignore success/failure without an active job
        Behaviors.same
    }

  private def busy(
      reportOrchestrator: ReportWithDataOrchestrator,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      reportZipExportService: ReportZipExportService,
      buffer: StashBuffer[ReportsExtractCommand],
      context: ActorContext[ReportsExtractCommand]
  ): Behavior[ReportsExtractCommand] =
    Behaviors.receiveMessage {
      case ExtractRequestSuccess(_, _) =>
        buffer.unstashAll(
          idle(
            reportOrchestrator,
            asyncFileRepository,
            s3Service,
            reportZipExportService,
            buffer,
            context
          )
        )
      case ExtractRequestFailure(error) =>
        context.log.error("Extract failed", error)
        buffer.unstashAll(
          idle(
            reportOrchestrator,
            asyncFileRepository,
            s3Service,
            reportZipExportService,
            buffer,
            context
          )
        )
      case msg =>
        val bufferRef = buffer.stash(msg)
        logger.trace(s"Buffer with size ${bufferRef.size}")
        Behaviors.same
    }

  private def handleExtractRequest(
      reportOrchestrator: ReportWithDataOrchestrator,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      reportZipExportService: ReportZipExportService,
      req: ExtractRequest,
      context: ActorContext[ReportsExtractCommand]
  ): scala.concurrent.Future[ReportsExtractCommand] = {
    import context.executionContext
    for {
      reportsId <- reportOrchestrator.getReportsFull(reportFilter = req.filters, user = req.requestedBy)
      source =
        if (req.requestedBy.userRole == Professionnel) {
          reportZipExportService.reportsSummaryZip(
            reportsId,
            req.requestedBy
          )
        } else {
          reportZipExportService.reportSummaryWithAttachmentsZip(
            reportsId,
            req.requestedBy
          )
        }
      fileName   = s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.zip"
      remotePath = s"extracts/$fileName"
      _ <- s3Service.uploadZipSource(source, remotePath)
      _ <- asyncFileRepository.update(req.fileId, fileName, remotePath)
    } yield ExtractRequestSuccess(req.fileId, req.requestedBy)
  }
}
