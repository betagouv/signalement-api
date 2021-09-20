package orchestrators

import models.Day
import models.Month
import models.Week
import models.Period
import models.ReportReviewStats
import play.api.libs.json.JsObject
import repositories._
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyStatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository
)(implicit val executionContext: ExecutionContext) {

  def getReportsCountByDate(id: UUID, request: Period): Future[Seq[(LocalDate, Int)]] =
    request match {
      case Day   => _report.getReportsCountByDay(id)
      case Week  => _report.getReportsCountByWeek(id)
      case Month => _report.getReportsCountByMonth(id)
    }

  def getReportsResponsesCountByDate(id: UUID, request: Period): Future[Seq[(LocalDate, Int)]] =
    request match {
      case Day   => _report.getReportsResponsesCountByDay(id)
      case Week  => _report.getReportsResponsesCountByWeek(id)
      case Month => _report.getReportsResponsesCountByMonth(id)
    }

  def getHosts(id: UUID) = _report.getHosts(id)

  def getReportsTagsDistribution(id: UUID) = _report.getReportsTagsDistribution(id)

  def getReportsStatusDistribution(id: UUID) = _report.getReportsStatusDistribution(id)

  def getReportResponseReview(id: UUID): Future[ReportReviewStats] =
    _event.getReportResponseReviews(id).map { events =>
      events.foldLeft(ReportReviewStats()) { case (acc, event) =>
        val review = event.details.as[JsObject].value.getOrElse("description", "").toString
        ReportReviewStats(
          acc.positive + (if (review.contains("Avis positif")) 1 else 0),
          acc.negative + (if (review.contains("Avis négatif")) 1 else 0)
        )
      }
    }

  def getResponseDelay(id: UUID): Future[Option[java.time.Duration]] =
    _event.getAvgTimeUntilEvent(id, REPORT_PRO_RESPONSE)
}
