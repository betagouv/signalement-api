package orchestrators

import config.AppConfigLoader
import models.CountByDate
import models.CurveTickDuration
import models.ReportFilter
import models.ReportReviewStats
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import repositories._
import utils.Constants.ActionEvent
import utils.Constants.ReportResponseReview
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository,
    _config: AppConfigLoader
)(implicit val executionContext: ExecutionContext) {

  private[this] lazy val startDate = _config.get.stats.backofficeAdminStartDate
  private[this] lazy val cutoff = _config.get.stats.globalStatsCutoff

  def getReportCount(reportFilter: ReportFilter): Future[Int] = {
    val boundedFilter = reportFilter.copy(
      start = reportFilter.start.orElse(Some(startDate)),
      end = reportFilter.end.orElse(cutoff.map(OffsetDateTime.now().minus(_).toLocalDate))
    )
    _report.count(boundedFilter)
  }

  def getReportsCountCurve(
      reportFilter: ReportFilter,
      ticks: Int,
      tickDuration: CurveTickDuration
  ): Future[Seq[CountByDate]] =
    tickDuration match {
      case CurveTickDuration.Month => _report.getMonthlyCount(reportFilter, ticks)
      case CurveTickDuration.Day   => _report.getDailyCount(reportFilter, ticks)
    }

  def getReportsTagsDistribution(companyId: Option[UUID]) = _report.getReportsTagsDistribution(companyId)

  def getReportsStatusDistribution(companyId: Option[UUID]) = _report.getReportsStatusDistribution(companyId)

  def getReportResponseReview(id: Option[UUID]): Future[ReportReviewStats] =
    _event.getReportResponseReviews(id).map { events =>
      events.foldLeft(ReportReviewStats()) { case (acc, event) =>
        val review = event.details.as[JsObject].value.getOrElse("description", JsString("")).toString
        ReportReviewStats(
          acc.positive + (if (review.contains(ReportResponseReview.Positive.entryName)) 1 else 0),
          acc.negative + (if (review.contains(ReportResponseReview.Negative.entryName)) 1 else 0)
        )
      }
    }

  def getReadAvgDelay(companyId: Option[UUID] = None) =
    _event.getAvgTimeUntilEvent(ActionEvent.REPORT_READING_BY_PRO, companyId)

  def getResponseAvgDelay(companyId: Option[UUID] = None) =
    _event.getAvgTimeUntilEvent(ActionEvent.REPORT_PRO_RESPONSE, companyId)
}
