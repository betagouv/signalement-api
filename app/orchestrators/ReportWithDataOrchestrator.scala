package orchestrators

import cats.implicits.toTraverseOps
import models.User
import models.company.Company
import models.event.Event
import models.report.ExistingReportResponse
import models.report.Report
import models.report.ReportFileApi
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReview
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
import repositories.reportengagementreview.ReportEngagementReviewRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import utils.Constants

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ReportWithData(
    report: Report,
    maybeCompany: Option[Company],
    events: Seq[(Event, Option[User])],
    responseOption: Option[ExistingReportResponse],
    consumerReviewOption: Option[ResponseConsumerReview],
    engagementReviewOption: Option[EngagementReview],
    companyEvents: Seq[(Event, Option[User])],
    files: Seq[ReportFileApi]
)

class ReportWithDataOrchestrator(
    reportOrchestrator: ReportOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    reviewRepository: ResponseConsumerReviewRepositoryInterface,
    engagementReviewRepository: ReportEngagementReviewRepositoryInterface
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  def getReportFull(uuid: UUID, userToCheckAuthorization: User): Future[Option[ReportWithData]] =
    reportOrchestrator
      .getVisibleReportForUser(uuid, userToCheckAuthorization)
      .flatMap { maybeReport =>
        maybeReport.map { reportWithMetadata =>
          val report = reportWithMetadata.report
          for {
            events       <- eventRepository.getEventsWithUsers(List(uuid), EventFilter())
            maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
            companyEvents <- report.companyId
              .map(companyId => eventRepository.getCompanyEventsWithUsers(companyId, EventFilter()))
              .getOrElse(Future.successful(List.empty))
            reportFiles            <- reportFileRepository.retrieveReportFiles(uuid)
            consumerReviewOption   <- reviewRepository.findByReportId(uuid).map(_.headOption)
            engagementReviewOption <- engagementReviewRepository.findByReportId(uuid).map(_.headOption)
          } yield {
            val responseOption = events
              .map(_._1)
              .find(_.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
              .map(_.details)
              .map(_.as[ExistingReportResponse])
            ReportWithData(
              report,
              maybeCompany,
              events,
              responseOption,
              consumerReviewOption,
              engagementReviewOption,
              companyEvents,
              reportFiles.map(ReportFileApi.build(_))
            )
          }
        } match {
          case Some(f) => f.map(Some(_))
          case None    => Future.successful(None)
        }
      }
}
