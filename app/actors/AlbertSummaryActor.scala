package actors

import models.report.Report
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import play.api.Logger
import play.api.libs.json.Json
import repositories.albert.AlbertClassification
import repositories.albert.AlbertClassificationRepositoryInterface
import services.AlbertService

import java.util.UUID
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object AlbertSummaryActor {
  sealed trait AlbertSummaryCommand
  final case class Summarize(report: Report)                             extends AlbertSummaryCommand
  final case class AlbertSummarySuccess(reportId: UUID)                  extends AlbertSummaryCommand
  final case class AlbertSummaryFailed(reportId: UUID, error: Throwable) extends AlbertSummaryCommand

  val logger: Logger = Logger(this.getClass)

  def create(
      albertService: AlbertService,
      albertClassificationRepository: AlbertClassificationRepositoryInterface
  ): Behavior[AlbertSummaryCommand] =
    Behaviors.setup { context =>
      import context.executionContext

      def summarize(report: Report): Future[Unit] = for {
        albertClassification <- albertService.classifyReport(report)
        albertCodeConsoRes   <- albertService.qualifyReportBasedOnCodeConso(report)
        maybeClassification = albertClassification.map(classificationJsonStr =>
          AlbertClassification
            .fromAlbertApi(
              report.id,
              Json.parse(classificationJsonStr),
              albertCodeConsoRes.map(Json.parse)
            )
        )
        _ <- maybeClassification match {
          case Some(albert) => albertClassificationRepository.createOrUpdate(albert)
          case None         => Future.unit
        }
      } yield ()

      Behaviors.receiveMessage {
        case Summarize(report) =>
          logger.info(s"Starting report summarization for report ${report.id}")
          context.pipeToSelf(summarize(report)) {
            case Success(_)         => AlbertSummarySuccess(report.id)
            case Failure(exception) => AlbertSummaryFailed(report.id, exception)
          }
          Behaviors.same

        case AlbertSummarySuccess(reportId) =>
          logger.info(s"Successfully summarized report $reportId")
          Behaviors.same

        case AlbertSummaryFailed(reportId, exception) =>
          logger.error(s"Failed to summarize report $reportId", exception)
          Behaviors.same
      }
    }
}
