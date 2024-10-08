package orchestrators

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOption
import controllers.error.AppError.WebsiteApiError
import models.CountByDate
import models.CurveTickDuration
import models.ReportReviewStats
import models.UserRole
import models.report._
import models.report.delete.ReportAdminActionType
import models.report.review.ResponseEvaluation
import orchestrators.StatsOrchestrator.computeStartingDate
import orchestrators.StatsOrchestrator.formatStatData
import orchestrators.StatsOrchestrator.restrictToReliableDates
import orchestrators.StatsOrchestrator.toPercentage
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
import repositories.reportengagementreview.ReportEngagementReviewRepositoryInterface
import services.WebsiteApiServiceInterface
import utils.Constants.ActionEvent._
import utils.Constants.ActionEvent
import utils.Constants.Departments

import java.sql.Timestamp
import java.time._
import java.util.Locale
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatsOrchestrator(
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    reportConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface,
    reportEngagementReviewRepository: ReportEngagementReviewRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    websiteApiService: WebsiteApiServiceInterface
)(implicit val executionContext: ExecutionContext) {

  def reportsCountBySubcategories(userRole: UserRole, filters: ReportsCountBySubcategoriesFilter): Future[ReportNodes] =
    for {
      maybeMinimizedAnomalies <- websiteApiService.fetchMinimizedAnomalies()
      minimizedAnomalies      <- maybeMinimizedAnomalies.liftTo[Future](WebsiteApiError)
      reportNodesFr <- reportRepository
        .reportsCountBySubcategories(userRole, filters, Locale.FRENCH)
        .map(StatsOrchestrator.buildReportNodes(minimizedAnomalies.fr, _))
      reportNodesEn <- reportRepository
        .reportsCountBySubcategories(userRole, filters, Locale.ENGLISH)
        .map(StatsOrchestrator.buildReportNodes(minimizedAnomalies.en, _))
    } yield ReportNodes(reportNodesFr, reportNodesEn)

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]] =
    for {
      postalCodeReportCountTuple <- reportRepository.countByDepartments(start, end)
      departmentsReportCountMap = formatCountByDepartments(postalCodeReportCountTuple)
    } yield departmentsReportCountMap

  private[orchestrators] def formatCountByDepartments(
      postalCodeReportCountTuple: Seq[(String, Int)]
  ): Seq[(String, Int)] = {
    val departmentsReportCountTuple: Seq[(String, Int)] =
      postalCodeReportCountTuple.map { case (partialPostalCode, count) =>
        (Departments.fromPostalCode(partialPostalCode).getOrElse(""), count)
      }
    departmentsReportCountTuple
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).sum)
      .toSeq
      .sortWith(_._2 > _._2)
  }

  def getReportCount(userRole: Option[UserRole], reportFilter: ReportFilter): Future[Int] =
    reportRepository.count(userRole, reportFilter)

  def fetchAdminActionEvents(companyId: UUID, reportAdminActionType: ReportAdminActionType) = {
    val action = reportAdminActionType match {
      case ReportAdminActionType.SolvedContractualDispute => SOLVED_CONTRACTUAL_DISPUTE
      case ReportAdminActionType.ConsumerThreatenByPro    => CONSUMER_THREATEN_BY_PRO
      case ReportAdminActionType.RefundBlackMail          => REFUND_BLACKMAIL
      case ReportAdminActionType.OtherReasonDeleteRequest => OTHER_REASON_DELETE_REQUEST
    }
    eventRepository.fetchEventCountFromActionEvents(companyId, action)

  }

  def getReportCountPercentage(
      userRole: Option[UserRole],
      filter: ReportFilter,
      basePercentageFilter: ReportFilter
  ): Future[Int] =
    for {
      count     <- reportRepository.count(userRole, filter)
      baseCount <- reportRepository.count(userRole, basePercentageFilter)
    } yield toPercentage(count, baseCount)

  def getReportCountPercentageWithinReliableDates(
      userRole: Option[UserRole],
      filter: ReportFilter,
      basePercentageFilter: ReportFilter
  ): Future[Int] =
    getReportCountPercentage(
      userRole,
      restrictToReliableDates(filter),
      restrictToReliableDates(basePercentageFilter)
    )

  def getReportsCountCurve(
      userRole: Option[UserRole],
      reportFilter: ReportFilter,
      ticks: Int = 12,
      tickDuration: CurveTickDuration = CurveTickDuration.Month
  ): Future[Seq[CountByDate]] =
    tickDuration match {
      case CurveTickDuration.Month => reportRepository.getMonthlyCount(userRole, reportFilter, ticks)
      case CurveTickDuration.Week  => reportRepository.getWeeklyCount(userRole, reportFilter, ticks)
      case CurveTickDuration.Day   => reportRepository.getDailyCount(userRole, reportFilter, ticks)
    }

  def getReportsCountPercentageCurve(
      userRole: Option[UserRole],
      reportFilter: ReportFilter,
      baseFilter: ReportFilter
  ): Future[Seq[CountByDate]] =
    for {
      rawCurve  <- getReportsCountCurve(userRole, reportFilter)
      baseCurve <- getReportsCountCurve(userRole, baseFilter)
    } yield rawCurve.sortBy(_.date).zip(baseCurve.sortBy(_.date)).map { case (a, b) =>
      CountByDate(
        count = toPercentage(a.count, b.count),
        date = a.date
      )
    }

  def getReportsTagsDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[ReportTag, Int]] =
    reportRepository.getReportsTagsDistribution(companyId, userRole)

  def getReportsStatusDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[String, Int]] =
    reportRepository.getReportsStatusDistribution(companyId, userRole)

  def getAcceptedResponsesDistribution(companyId: UUID, userRole: UserRole): Future[Map[ExistingResponseDetails, Int]] =
    reportRepository.getAcceptedResponsesDistribution(companyId, userRole)

  def getReportResponseReview(id: Option[UUID]): Future[ReportReviewStats] =
    reportConsumerReviewRepository.findByCompany(id).map { events =>
      events.foldLeft(ReportReviewStats()) { case (acc, event) =>
        ReportReviewStats(
          positive = acc.positive + (if (event.evaluation == ResponseEvaluation.Positive) 1 else 0),
          neutral = acc.neutral + (if (event.evaluation == ResponseEvaluation.Neutral) 1 else 0),
          negative = acc.negative + (if (event.evaluation == ResponseEvaluation.Negative) 1 else 0)
        )
      }
    }

  def getReportEngagementReview(id: Option[UUID]): Future[ReportReviewStats] =
    reportEngagementReviewRepository.findByCompany(id).map { events =>
      events.foldLeft(ReportReviewStats()) { case (acc, event) =>
        ReportReviewStats(
          positive = acc.positive + (if (event.evaluation == ResponseEvaluation.Positive) 1 else 0),
          neutral = acc.neutral + (if (event.evaluation == ResponseEvaluation.Neutral) 1 else 0),
          negative = acc.negative + (if (event.evaluation == ResponseEvaluation.Negative) 1 else 0)
        )
      }
    }

  def getReadAvgDelay(companyId: Option[UUID] = None) =
    eventRepository.getAvgTimeUntilEvent(ActionEvent.REPORT_READING_BY_PRO, companyId)

  def getResponseAvgDelay(companyId: Option[UUID] = None, userRole: UserRole): Future[Option[Duration]] = {
    val onlyProShareable = userRole == UserRole.Professionnel
    eventRepository.getAvgTimeUntilEvent(
      action = ActionEvent.REPORT_PRO_RESPONSE,
      companyId = companyId,
      onlyProShareable = onlyProShareable
    )
  }

  def getProReportTransmittedStat(ticks: Int) =
    eventRepository
      .getProReportStat(
        ticks,
        computeStartingDate(ticks),
        NonEmptyList.of(
          REPORT_READING_BY_PRO,
          REPORT_CLOSED_BY_NO_READING,
          REPORT_CLOSED_BY_NO_ACTION,
          EMAIL_PRO_NEW_REPORT,
          REPORT_PRO_RESPONSE
        )
      )
      .map(formatStatData(_, ticks))

  def dgccrfAccountsCurve(ticks: Int) =
    accessTokenRepository
      .dgccrfAccountsCurve(ticks)
      .map(formatStatData(_, ticks))

  def dgccrfSubscription(ticks: Int) =
    accessTokenRepository
      .dgccrfSubscription(ticks)
      .map(formatStatData(_, ticks))

  def dgccrfActiveAccountsCurve(ticks: Int) =
    accessTokenRepository
      .dgccrfActiveAccountsCurve(ticks)
      .map(formatStatData(_, ticks))

  def dgccrfControlsCurve(ticks: Int) =
    accessTokenRepository
      .dgccrfControlsCurve(ticks)
      .map(formatStatData(_, ticks))
}

object StatsOrchestrator {

  private[orchestrators] def buildReportNodes(
      arbo: List[ArborescenceNode],
      results: Seq[(String, List[String], Int, Int)]
  ): List[ReportNode] = {
    val merged = results.map { case (cat, subcat, count, reclamations) => (cat :: subcat, count, reclamations) }
    val tree   = ReportNode("", 0, 0, List.empty, List.empty, None)

    arbo.foreach { arborescenceNode =>
      val res          = merged.find(_._1 == arborescenceNode.path.map(_._1).toList)
      val count        = res.map(_._2).getOrElse(0)
      val reclamations = res.map(_._3).getOrElse(0)
      createOrUpdateReportNode(arborescenceNode.path, count, reclamations, tree)
    }

    val arboPathes = arbo.map(_.path.map(_._1).toList)
    merged.foreach { case (path, count, reclamations) =>
      if (!arboPathes.contains(path)) createOrUpdateReportNodeOld(path, count, reclamations, tree)
    }

    tree.children
  }

  @tailrec
  private def createOrUpdateReportNode(
      subcats: Vector[(String, NodeInfo)],
      count: Int,
      reclamations: Int,
      tree: ReportNode
  ): Unit = {
    tree.count += count
    tree.reclamations += reclamations
    subcats match {
      case (path, nodeInfo) +: rest =>
        tree.children.find(_.name == path) match {
          case Some(child) => createOrUpdateReportNode(rest, count, reclamations, child)
          case None =>
            val reportNode = ReportNode(path, 0, 0, List.empty, nodeInfo.tags, Some(nodeInfo.id))
            tree.children = reportNode :: tree.children
            createOrUpdateReportNode(rest, count, reclamations, reportNode)

        }
      case _ => ()
    }
  }

  @tailrec
  private def createOrUpdateReportNodeOld(
      subcats: List[String],
      count: Int,
      reclamations: Int,
      tree: ReportNode
  ): Unit = {
    tree.count += count
    tree.reclamations += reclamations
    subcats match {
      case path :: rest =>
        tree.children.find(_.name == path) match {
          case Some(child) => createOrUpdateReportNodeOld(rest, count, reclamations, child)
          case None =>
            val reportNode = ReportNode(path, 0, 0, List.empty, List.empty, None)
            tree.children = reportNode :: tree.children
            createOrUpdateReportNodeOld(rest, count, reclamations, reportNode)

        }
      case _ => ()
    }
  }

  private[orchestrators] val reliableStatsStartDate = OffsetDateTime.parse("2019-01-01T00:00:00Z")

  private[orchestrators] def restrictToReliableDates(reportFilter: ReportFilter): ReportFilter =
    // Percentages would be messed up if we look at really old data or really fresh one
    reportFilter.copy(
      start = Some(reliableStatsStartDate),
      end = Some(OffsetDateTime.now().minusDays(30))
    )

  private[orchestrators] def toPercentage(numerator: Int, denominator: Int): Int =
    if (denominator == 0) 0
    else Math.max(0, Math.min(100, numerator * 100 / denominator))

  private[orchestrators] def computeStartingDate(ticks: Int): OffsetDateTime =
    OffsetDateTime.now().minusMonths(ticks.toLong - 1L).withDayOfMonth(1)

  /** Fill data with default value when there missing data in database
    */
  private[orchestrators] def formatStatData(data: Vector[(Timestamp, Int)], ticks: Int): Seq[CountByDate] = {

    val countByDateList = data.map { case (date, count) => CountByDate(count, date.toLocalDateTime.toLocalDate) }

    if (ticks - data.length > 0) {
      val upperBound = LocalDate.now().withDayOfMonth(1)
      val lowerBound = upperBound.minusMonths(ticks.toLong - 1L)

      val minAvailableDatabaseDataDate = countByDateList.map(_.date).minOption.getOrElse(lowerBound)
      val maxAvailableDatabaseDataDate = countByDateList.map(_.date).maxOption.getOrElse(upperBound)

      val missingMonthsLowerBound = Period.between(lowerBound, minAvailableDatabaseDataDate)
      val missingMonthsUpperBound = Period.between(maxAvailableDatabaseDataDate, upperBound)

      if (missingMonthsLowerBound.getMonths == 0 && missingMonthsUpperBound.getMonths == 0) {
        // No data , filling the data with default value
        Seq
          .iterate(lowerBound, ticks)(_.plusMonths(1))
          .map(CountByDate(0, _))
      } else {
        // Missing data , filling the data with default value
        val missingLowerBoundData =
          Seq.iterate(lowerBound, missingMonthsLowerBound.getMonths)(_.minusMonths(1L)).map(CountByDate(0, _))

        val missingUpperBoundData =
          Seq
            .iterate(maxAvailableDatabaseDataDate.plusMonths(1), missingMonthsUpperBound.getMonths)(_.plusMonths(1))
            .map(CountByDate(0, _))

        missingLowerBoundData ++ countByDateList ++ missingUpperBoundData
      }
    } else countByDateList

  }

}
