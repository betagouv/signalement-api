package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.CannotReviewReportResponse
import controllers.error.AppError.EngagementNotFound
import controllers.error.AppError.ReportNotFound
import controllers.error.AppError.ServerError
import models.User
import models.event.Event
import models.engagement.EngagementApi
import models.engagement.EngagementId
import models.report.ReportResponse
import models.report.ReportStatus.hasResponse
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReviewApi
import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation
import play.api.Logger
import play.api.libs.json.Json
import repositories.event.EventRepositoryInterface
import repositories.engagement.EngagementRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportengagementreview.ReportEngagementReviewRepositoryInterface
import utils.Constants.ActionEvent
import utils.Constants.EventType

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EngagementOrchestrator(
    engagementRepository: EngagementRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    eventRepository: EventRepositoryInterface,
    reportRepository: ReportRepositoryInterface,
    reportEngagementReviewRepository: ReportEngagementReviewRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def listForUser(proUser: User): Future[Seq[EngagementApi]] =
    for {
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
      engagements <- engagementRepository.listEngagementsWithEventsAndReport(
        Some(proUser.userRole),
        companiesWithAccesses.map(_.company.id)
      )
    } yield engagements.flatMap { case (((report, engagement), promiseEvent), resolutionEvent) =>
      val today = OffsetDateTime.now().toLocalDate
      for {
        _ <- resolutionEvent match {
          case None        => Some(())
          case Some(event) => if (event.creationDate.toLocalDate.plusDays(1).isBefore(today)) None else Some(())
        }
        reportResponse  <- promiseEvent.details.asOpt[ReportResponse]
        responseDetails <- reportResponse.responseDetails
      } yield EngagementApi(
        engagement.id,
        report,
        responseDetails,
        reportResponse.otherResponseDetails,
        engagement.expirationDate,
        resolutionEvent.map(_.creationDate)
      )
    }

  def check(proUser: User, engagementId: EngagementId): Future[Unit] =
    for {
      maybeEngagement       <- engagementRepository.get(engagementId)
      engagement            <- maybeEngagement.liftTo[Future](EngagementNotFound(engagementId))
      maybeReport           <- reportRepository.getFor(Some(proUser.userRole), engagement.reportId)
      report                <- maybeReport.liftTo[Future](ReportNotFound(engagement.reportId))
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
      _ <- report.report.companyId match {
        case Some(companyId) if companiesWithAccesses.map(_.company.id).contains(companyId) => Future.unit
        case _ => Future.failed(ReportNotFound(engagement.reportId))
      }
      event <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.report.id),
          report.report.companyId,
          Some(proUser.id),
          OffsetDateTime.now(),
          EventType.PRO,
          ActionEvent.REPORT_PRO_ENGAGEMENT_HONOURED,
          Json.obj()
        )
      )
      _ <- engagementRepository.check(engagementId, event.id)
    } yield ()

  def uncheck(proUser: User, engagementId: EngagementId) =
    for {
      maybeEngagement       <- engagementRepository.get(engagementId)
      engagement            <- maybeEngagement.liftTo[Future](EngagementNotFound(engagementId))
      maybeReport           <- reportRepository.getFor(Some(proUser.userRole), engagement.reportId)
      report                <- maybeReport.liftTo[Future](ReportNotFound(engagement.reportId))
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
      _ <- report.report.companyId match {
        case Some(companyId) if companiesWithAccesses.map(_.company.id).contains(companyId) => Future.unit
        case _ => Future.failed(ReportNotFound(engagement.reportId))
      }
      _ <- engagementRepository.uncheck(engagementId)
      _ <- eventRepository.deleteEngagement(report.report.id)
    } yield ()

  def removeEngagement(reportId: UUID): Future[Unit] =
    findEngagementReview(reportId).flatMap {
      case Some(engagementReview) =>
        reportEngagementReviewRepository.delete(engagementReview.id).map(_ => ())
      case None => Future.unit
    }

  def findEngagementReview(reportId: UUID): Future[Option[EngagementReview]] =
    reportEngagementReviewRepository.findByReportId(reportId) map {
      case Nil =>
        logger.info(s"No engagement review found for report $reportId")
        None
      case review :: Nil => Some(review)
      case _             => throw ServerError(s"More than one engagement review for report id $reportId")
    }

  def handleEngagementReview(
      reportId: UUID,
      reviewApi: ResponseConsumerReviewApi
  ): Future[Unit] = {

    logger.info(s"Engagement for report $reportId - the consumer give a review on engagement")

    for {
      report <- reportRepository.get(reportId)
      _ = logger.debug(s"Validating report")
      _ <- report match {
        case Some(report) if hasResponse(report) =>
          Future.successful(report)
        case Some(_) =>
          logger.warn(s"Report with id ${reportId} has no response yet, cannot review this engagement response")
          Future.failed(CannotReviewReportResponse(reportId))
        case None =>
          logger.warn(s"Report with id ${reportId} does not exist, cannot review this engagement response")
          Future.failed(CannotReviewReportResponse(reportId))
      }
      _ = logger.debug(s"Report validated")
      reviews <- reportEngagementReviewRepository.findByReportId(reportId)
      _ <- reviews.headOption match {
        case Some(review) =>
          updateEngagementReview(review.copy(evaluation = reviewApi.evaluation, details = reviewApi.details))
        case None =>
          createEngagementReview(reportId, reviewApi.evaluation)
      }
    } yield ()
  }

  private def updateEngagementReview(review: EngagementReview) =
    reportEngagementReviewRepository.update(
      review.id,
      review.copy(
        evaluation = review.evaluation,
        details = review.details
      )
    )
  private def createEngagementReview(reportId: UUID, evaluation: ResponseEvaluation): Future[Event] =
    reportEngagementReviewRepository
      .createOrUpdate(
        EngagementReview(
          ResponseConsumerReviewId.generateId(),
          reportId,
          evaluation,
          creationDate = OffsetDateTime.now(),
          None
        )
      )
      .flatMap(_ =>
        eventRepository.create(
          Event(
            id = UUID.randomUUID(),
            reportId = Some(reportId),
            companyId = None,
            userId = None,
            creationDate = OffsetDateTime.now(),
            eventType = EventType.CONSO,
            action = ActionEvent.REPORT_REVIEW_ON_ENGAGEMENT
          )
        )
      )
}
