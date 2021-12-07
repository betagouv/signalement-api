package orchestrators

import cats.data.NonEmptyList
import models.CountByDate
import models.CurveTickDuration
import models.ReportFilter
import models.ReportResponseType
import models.ReportReviewStats
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import repositories._
import utils.Constants.ActionEvent
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent.REPORT_READING_BY_PRO
import utils.Constants.ReportResponseReview

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatsOrchestrator @Inject() (
    _report: ReportRepository,
    _event: EventRepository
)(implicit val executionContext: ExecutionContext) {

  def getReportCount(reportFilter: ReportFilter): Future[Int] =
    _report.count(reportFilter)

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

  def getProReportTransmittedStat(ticks: Int) =
    _event
      .getProReportStat(
        ticks,
        NonEmptyList.of(
          REPORT_READING_BY_PRO,
          REPORT_CLOSED_BY_NO_READING,
          REPORT_CLOSED_BY_NO_ACTION,
          EMAIL_PRO_NEW_REPORT,
          REPORT_PRO_RESPONSE
        )
      )
      .map(_.map { case (date, count) => CountByDate(count, date.toLocalDateTime.toLocalDate) })
      .map(handleMissingData(_, ticks))

  def getProReportResponseStat(ticks: Int, responseTypes: NonEmptyList[ReportResponseType]) =
    _event
      .getProReportResponseStat(
        ticks,
        responseTypes
      )
      .map(_.map { case (date, count) => CountByDate(count, date.toLocalDateTime.toLocalDate) })
      .map(handleMissingData(_, ticks))

  /** Temporary means to fill data with default value, will not be necessary in a few months
    */
  private def handleMissingData(data: Vector[CountByDate], ticks: Int): Seq[CountByDate] = {
    val diff = ticks - data.length
    if (diff >= 0) {
      val minDate = data.map(_.date).min
      val missingData = Seq.iterate(minDate, diff)(_.minusMonths(1)).map(CountByDate(0, _))
      missingData ++ data
    } else data
  }

}
