package orchestrators

import org.apache.pekko.Done
import controllers.error.AppError.CannotReviewReportResponse
import controllers.error.AppError.ServerError
import models.report.ReportStatus.hasResponse
import models.report.review.ResponseConsumerReview
import models.report.review.ConsumerReviewApi
import models.report.review.ResponseConsumerReviewId
import play.api.Logger
import utils.Constants.ActionEvent
import utils.Constants.EventType
import models.event.Event
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportConsumerReviewOrchestrator(
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    responseConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {
  val logger = Logger(this.getClass)

  def remove(reportId: UUID): Future[Done] =
    find(reportId).flatMap {
      case Some(responseConsumerReview) =>
        responseConsumerReviewRepository.delete(responseConsumerReview.id).map(_ => Done)
      case None => Future.successful(Done)
    }

  def find(reportId: UUID): Future[Option[ResponseConsumerReview]] =
    responseConsumerReviewRepository.findByReportId(reportId) map {
      case Nil =>
        logger.info(s"No review found for report $reportId")
        None
      case review :: Nil => Some(review)
      case _             => throw ServerError(s"More than one consumer review for report id $reportId")
    }

  def find(reportIds: List[UUID]): Future[Map[UUID, Option[ResponseConsumerReview]]] =
    responseConsumerReviewRepository.findByReportIds(reportIds)

  def handleReviewOnReportResponse(
      reportId: UUID,
      reviewApi: ConsumerReviewApi
  ): Future[Unit] = {

    logger.info(s"Report ${reportId} - the consumer give a review on response")

    for {
      report <- reportRepository.get(reportId)
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
      reviews <- responseConsumerReviewRepository.findByReportId(reportId)
      _ <- reviews.headOption match {
        case Some(review) =>
          updateReview(review.copy(evaluation = reviewApi.evaluation, details = reviewApi.details))
        case None =>
          createReview(reportId, reviewApi)
      }
    } yield ()
  }

  def deleteDetails(reportId: UUID): Future[Unit] = for {
    reviews <- responseConsumerReviewRepository.findByReportId(reportId)
    _ <- reviews match {
      case review :: _ => responseConsumerReviewRepository.update(review.id, review.copy(details = Some("")))
      case _           => Future.unit
    }
  } yield ()

  private def updateReview(review: ResponseConsumerReview) =
    responseConsumerReviewRepository.update(
      review.id,
      review.copy(
        evaluation = review.evaluation,
        details = review.details
      )
    )

  private def createReview(reportId: UUID, review: ConsumerReviewApi): Future[Event] =
    responseConsumerReviewRepository
      .createOrUpdate(
        ResponseConsumerReview(
          ResponseConsumerReviewId.generateId(),
          reportId,
          evaluation = review.evaluation,
          creationDate = OffsetDateTime.now(),
          details = review.details
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
            action = ActionEvent.REPORT_REVIEW_ON_RESPONSE
          )
        )
      )

}
