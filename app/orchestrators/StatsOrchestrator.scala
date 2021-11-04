package orchestrators

import config.AppConfigLoader
import models.{CountByDate, CurveTickDuration, ReportFilter, ReportReviewStats}
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import repositories._
import utils.Constants.ReportStatus._
import utils.Constants.ActionEvent
import utils.Constants.ReportResponseReview

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository,
    appConfigLoader: AppConfigLoader
)(implicit val executionContext: ExecutionContext) {

  private[this] lazy val cutoff = appConfigLoader.get.stats.globalStatsCutoff

  def getReportCount(reportFilter: ReportFilter): Future[Int] = {
    _report.count(reportFilter)
  }

//  def getReportWithStatusPercent(
//      status: Seq[ReportStatusValue],
//      tags: Seq[String],
//      baseStatus: Seq[ReportStatusValue] = ReportStatus.reportStatusList,
//      baseTags: Seq[String],
//      companyId: Option[UUID] = None
//  ): Future[Int] =
//    for {
//      count <- _report.count(ReportFilter(status = status, tags = tags cutoff = cutoff, companyIds = companyId))
//      baseCount <- _report.countWithStatus(status = baseStatus, tags = baseTags, cutoff = cutoff, companyId = companyId)
//    } yield count * 100 / baseCount

//  def getReportHavingWebsitePercentage(companyId: Option[UUID] = None): Future[Int] =
//    for {
//      count <- _report.countWithStatus(
//        status = ReportStatus.reportStatusList,
//        cutoff = cutoff,
//        withWebsite = Some(true),
//        companyId = companyId
//      )
//      baseCount <- _report.countWithStatus(
//        status = ReportStatus.reportStatusList,
//        cutoff = cutoff,
//        companyId = companyId
//      )
//    } yield count * 100 / baseCount

  def getReportsCountCurve(
      reportFilter: ReportFilter,
      ticks: Int = 7,
      tickDuration: CurveTickDuration = CurveTickDuration.Month
  ): Future[Seq[CountByDate]] =
    tickDuration match {
      case CurveTickDuration.Month => _report.getMonthlyCount(reportFilter, ticks)
      case CurveTickDuration.Day   => _report.getDailyCount(reportFilter, ticks)
    }

//  def getReportsCountCurve(
//      companyId: Option[UUID] = None,
//      status: Seq[ReportStatusValue] = List(),
//      tags: Seq[String],
//      ticks: Int = 7,
//      tickDuration: CurveTickDuration = CurveTickDuration.Month
//  ): Future[Seq[CountByDate]] =
//    tickDuration match {
//      case CurveTickDuration.Month => _report.getMonthlyCount(companyId, status, tags, ticks)
//      case CurveTickDuration.Day   => _report.getDailyCount(companyId, status, tags, ticks)
//    }

//  def getReportWithStatusPercentageCurve(
//      status: Seq[ReportStatusValue],
//      baseStatus: Seq[ReportStatusValue] = Seq(),
//      tags: Seq[String],
//      companyId: Option[UUID] = None,
//      ticks: Int,
//      tickDuration: CurveTickDuration = CurveTickDuration.Month
//  ) =
//    for {
//      counts <- getReportsCountCurve(
//        companyId = companyId,
//        status = status,
//        tags = tags,
//        ticks = ticks,
//        tickDuration = tickDuration
//      )
//      baseCounts <- getReportsCountCurve(
//        companyId = companyId,
//        status = baseStatus,
//        tags = tags,
//        ticks = ticks,
//        tickDuration = tickDuration
//      )
//    } yield baseCounts.map(monthlyBaseCount =>
//      CountByDate(
//        counts
//          .find(_.date == monthlyBaseCount.date)
//          .map(_.count)
//          .map(_ * 100 / Math.max(monthlyBaseCount.count, 1))
//          .getOrElse(0),
//        monthlyBaseCount.date
//      )
//    )

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
