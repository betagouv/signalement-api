package orchestrators

import models.CompanyReportsCountByDay
import models.CompanyReportsCountByMonth
import models.CompanyReportsCountByWeek
import models.CompanyReportsCountPeriod
import models.ReportReviewStats
import play.api.libs.json.JsObject
import repositories._

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyStatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository
)(implicit val executionContext: ExecutionContext) {

  def getReportsCountByDate(id: UUID, request: CompanyReportsCountPeriod): Future[Seq[(LocalDate, Int)]] =
    request match {
      case CompanyReportsCountByDay   => _report.getReportsCountByDay(id)
      case CompanyReportsCountByWeek  => _report.getReportsCountByWeek(id)
      case CompanyReportsCountByMonth => _report.getReportsCountByMonth(id)
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
}
