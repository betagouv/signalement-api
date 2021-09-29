package orchestrators

import models.ReportReviewStats
import models.CountByDate
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
    configuration: Configuration
)(implicit val executionContext: ExecutionContext) {

  private[this] lazy val cutoff =
    configuration.getOptional[String]("play.stats.globalStatsCutoff").map(java.time.Duration.parse(_))

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

  def getReportsCountDaily(
      status: Seq[ReportStatusValue] = List(),
      companyId: Option[UUID] = None,
      ticks: Int
  ): Future[Seq[CountByDate]] =
    _report.dailyCount(status = status, companyId = companyId, ticks = ticks)

  def getReportsCountMonthly(
      status: Seq[ReportStatusValue] = List(),
      companyId: Option[UUID] = None,
      ticks: Int
  ): Future[Seq[CountByDate]] =
    _report.monthlyCount(status = status, companyId = companyId, ticks = ticks)

  def getMonthlyReportWithStatusPercentage(
      status: Seq[ReportStatusValue],
      baseStatus: Seq[ReportStatusValue] = List(),
      companyId: Option[UUID] = None,
      ticks: Int
  ) =
    for {
      monthlyCounts <- _report.monthlyCount(status = status, companyId = companyId, ticks = ticks)
      monthlyBaseCounts <- _report.monthlyCount(status = baseStatus, companyId = companyId, ticks = ticks)
    } yield monthlyBaseCounts.map(monthlyBaseCount =>
      CountByDate(
        monthlyCounts
          .find(_.date == monthlyBaseCount.date)
          .map(_.count)
          .getOrElse(0) * 100 / monthlyBaseCount.count,
        monthlyBaseCount.date
      )
    )

//  def getReportsCountByDate(id: UUID, request: Period): Future[Seq[(LocalDate, Int)]] =
//    request match {
//      case Day   => _report.getReportsCountByDay(id)
//      case Week  => _report.getReportsCountByWeek(id)
//      case Month => _report.getReportsCountByMonth(id)
//    }
//
//  def getReportsResponsesCountByDate(id: UUID, request: Period): Future[Seq[(LocalDate, Int)]] =
//    request match {
//      case Day   => _report.getReportsResponsesCountByDay(id)
//      case Week  => _report.getReportsResponsesCountByWeek(id)
//      case Month => _report.getReportsResponsesCountByMonth(id)
//    }

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
