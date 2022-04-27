package orchestrators

import cats.implicits.catsSyntaxMonadError
import controllers.error.AppError.CannotReviewReportResponse
import controllers.error.AppError.ReviewAlreadyExists
import controllers.error.AppError.ServerError
import models.report.ReportStatus.hasResponse
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewApi
import models.report.review.ResponseConsumerReviewId
import play.api.Logger
import utils.Constants.ActionEvent
import utils.Constants.EventType
import io.scalaland.chimney.dsl.TransformerOps
import models.event.Event
import repositories.event.EventRepository
import repositories.report.ReportRepository
import repositories.reportconsumerreview.ResponseConsumerReviewRepository

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportConsumerReviewOrchestrator @Inject() (
    reportRepository: ReportRepository,
    eventRepository: EventRepository,
    responseConsumerReviewRepository: ResponseConsumerReviewRepository
)(implicit
    val executionContext: ExecutionContext
) {
  val logger = Logger(this.getClass)

  def find(reportId: UUID): Future[Option[ResponseConsumerReview]] =
    responseConsumerReviewRepository.find(reportId) map {
      case Nil =>
        logger.info(s"No review found for report $reportId")
        None
      case review :: Nil => Some(review)
      case _             => throw ServerError(s"More than one consumer review for report id $reportId")
    }

  def handleReviewOnReportResponse(
      reportId: UUID,
      responseConsumerReviewApi: ResponseConsumerReviewApi
  ): Future[Event] = {

    logger.info(s"Report ${reportId} - the consumer give a review on response")

    for {
      report <- reportRepository.getReport(reportId)
      _ = logger.debug(s"Validating report")
      _ <- report match {
        case Some(report) if hasResponse(report) =>
          Future.successful(report)
        case Some(_) =>
          logger.warn(s"Report with id $reportId has no response yet, cannot review this report response")
          Future.failed(CannotReviewReportResponse(reportId))
        case None =>
          logger.warn(s"Report with id $reportId does not exist, cannot review this report response")
          Future.failed(CannotReviewReportResponse(reportId))
      }
      _ = logger.debug(s"Report validated")
      responseConsumerReview = responseConsumerReviewApi
        .into[ResponseConsumerReview]
        .withFieldConst(_.reportId, reportId)
        .withFieldConst(_.creationDate, OffsetDateTime.now())
        .withFieldConst(_.id, ResponseConsumerReviewId.generateId())
        .transform
      _ = logger.debug(s"Checking if review already exists")
      _ <- responseConsumerReviewRepository.find(reportId).ensure(ReviewAlreadyExists) {
        case Nil => true
        case _ =>
          logger.warn(s"Review already exist for report with id $reportId")
          false
      }
      _ = logger.debug(s"Saving review")
      _ <- responseConsumerReviewRepository.create(responseConsumerReview)
      _ = logger.debug(s"Creating event")
      event <- eventRepository.createEvent(
        Event(
          id = UUID.randomUUID(),
          reportId = Some(reportId),
          companyId = None,
          userId = None,
          creationDate = OffsetDateTime.now(),
          eventType = EventType.CONSO,
          action = ActionEvent.REPORT_REVIEW_ON_RESPONSE
        )
      )
    } yield event
  }

}
