package orchestrators

import config.AppConfigLoader
import models.CountByDate
import models.CurveTickDuration
import models.ReportReviewStats
import play.api.Configuration
import play.api.libs.json.JsObject
import repositories._
import utils.Constants.ReportStatus._
import utils.Constants.ActionEvent
import utils.Constants.ReportResponseReview
import utils.Constants.ReportStatus

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository,
    appConfigLoader: AppConfigLoader
)(implicit val executionContext: ExecutionContext) {

  private[this] lazy val cutoff = appConfigLoader.signalConsoConfiguration.stats.globalStatsCutoff

  def getReportCount(companyId: Option[UUID] = None): Future[Int] =
    _report.count(companyId)

  def getReportWithStatusPercent(
      status: Seq[ReportStatusValue],
      baseStatus: Seq[ReportStatusValue] = ReportStatus.reportStatusList,
      companyId: Option[UUID] = None
  ): Future[Int] =
    for {
      count <- _report.countWithStatus(status = status, cutoff = cutoff, companyId = companyId)
      baseCount <- _report.countWithStatus(status = baseStatus, cutoff = cutoff, companyId = companyId)
    } yield count * 100 / baseCount

  def getReportHavingWebsitePercentage(companyId: Option[UUID] = None): Future[Int] =
    for {
      count <- _report.countWithStatus(
        status = ReportStatus.reportStatusList,
        cutoff = cutoff,
        withWebsite = Some(true),
        companyId = companyId
      )
      baseCount <- _report.countWithStatus(
        status = ReportStatus.reportStatusList,
        cutoff = cutoff,
        companyId = companyId
      )
    } yield count * 100 / baseCount

  def getReportsCountCurve(
      companyId: Option[UUID] = None,
      status: Seq[ReportStatusValue] = List(),
      ticks: Int = 7,
      tickDuration: CurveTickDuration = CurveTickDuration.Month
  ): Future[Seq[CountByDate]] =
    tickDuration match {
      case CurveTickDuration.Month => _report.getMonthlyCount(companyId, status, ticks)
      case CurveTickDuration.Day   => _report.getDailyCount(companyId, status, ticks)
    }

  def getReportWithStatusPercentageCurve(
      status: Seq[ReportStatusValue],
      baseStatus: Seq[ReportStatusValue] = Seq(),
      companyId: Option[UUID] = None,
      ticks: Int,
      tickDuration: CurveTickDuration = CurveTickDuration.Month
  ) =
    for {
      counts <- getReportsCountCurve(companyId, status, ticks, tickDuration)
      baseCounts <- getReportsCountCurve(companyId, baseStatus, ticks, tickDuration)
    } yield baseCounts.map(monthlyBaseCount =>
      CountByDate(
        counts
          .find(_.date == monthlyBaseCount.date)
          .map(_.count)
          .map(_ * 100 / Math.max(monthlyBaseCount.count, 1))
          .getOrElse(0),
        monthlyBaseCount.date
      )
    )

  def getReportsTagsDistribution(companyId: Option[UUID]) = _report.getReportsTagsDistribution(companyId)

  def getReportsStatusDistribution(companyId: Option[UUID]) = _report.getReportsStatusDistribution(companyId)

  def getReportResponseReview(id: Option[UUID]): Future[ReportReviewStats] =
    _event.getReportResponseReviews(id).map { events =>
      events.foldLeft(ReportReviewStats()) { case (acc, event) =>
        val review = event.details.as[JsObject].value.getOrElse("description", "").toString
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
