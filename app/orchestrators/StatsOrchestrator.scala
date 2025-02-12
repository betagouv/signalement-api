package orchestrators

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOption
import controllers.error.AppError.WebsiteApiError
import models.CountByDate
import models.CurveTickDuration
import models.ReportReviewStats
import models.User
import models.UserRole
import models.report._
import models.report.delete.ReportAdminActionType
import models.report.review.ResponseEvaluation
import orchestrators.StatsOrchestrator.computeStartingDate
import orchestrators.StatsOrchestrator.formatStatData
import orchestrators.StatsOrchestrator.toPercentage
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
import repositories.reportengagementreview.ReportEngagementReviewRepositoryInterface
import repositories.subcategorylabel.SubcategoryLabel
import repositories.subcategorylabel.SubcategoryLabelRepositoryInterface
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
    websiteApiService: WebsiteApiServiceInterface,
    subcategoryLabelRepository: SubcategoryLabelRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  def downloadReportsCountBySubcategories(
      user: User,
      filters: ReportsCountBySubcategoriesFilter,
      locale: Locale
  ): Future[String] = for {
    maybeMinimizedAnomalies <- websiteApiService.fetchMinimizedAnomalies()
    minimizedAnomalies      <- maybeMinimizedAnomalies.liftTo[Future](WebsiteApiError)
    res <-
      if (locale == Locale.FRENCH)
        reportRepository
          .reportsCountBySubcategories(user, filters, Locale.FRENCH)
          .map(StatsOrchestrator.buildCSV(minimizedAnomalies.fr, _))
      else
        reportRepository
          .reportsCountBySubcategories(user, filters, locale)
          .map(StatsOrchestrator.buildCSV(minimizedAnomalies.en, _))

  } yield {
    val header = "Catégorie,Sous-catégorie,Nombre,Réclamations"
    val lines  = res.map { case (a, b, c, d) => s"$a,$b,$c,$d" }.mkString("\n")
    s"$header\n$lines"
  }

  def reportsCountBySubcategories(user: User, filters: ReportsCountBySubcategoriesFilter): Future[ReportNodes] =
    for {
      maybeMinimizedAnomalies <- websiteApiService.fetchMinimizedAnomalies()
      minimizedAnomalies      <- maybeMinimizedAnomalies.liftTo[Future](WebsiteApiError)
      labels                  <- subcategoryLabelRepository.list()
      reportNodesFr <- reportRepository
        .reportsCountBySubcategories(user, filters, Locale.FRENCH)
        .map(StatsOrchestrator.buildReportNodes(labels, Locale.FRENCH, minimizedAnomalies.fr, _))
      reportNodesEn <- reportRepository
        .reportsCountBySubcategories(user, filters, Locale.ENGLISH)
        .map(StatsOrchestrator.buildReportNodes(labels, Locale.ENGLISH, minimizedAnomalies.en, _))
    } yield ReportNodes(reportNodesFr, reportNodesEn)

  def countByDepartments(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Future[Seq[(String, Int)]] =
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

  def getReportCount(user: Option[User], reportFilter: ReportFilter): Future[Int] =
    reportRepository.count(user, reportFilter)

  def fetchAdminActionEvents(companyId: UUID, reportAdminActionType: ReportAdminActionType) = {
    val action = reportAdminActionType match {
      case ReportAdminActionType.SolvedContractualDispute => SOLVED_CONTRACTUAL_DISPUTE
      case ReportAdminActionType.ConsumerThreatenByPro    => CONSUMER_THREATEN_BY_PRO
      case ReportAdminActionType.RefundBlackMail          => REFUND_BLACKMAIL
      case ReportAdminActionType.RGPDDeleteRequest        => RGPD_DELETE_REQUEST
    }
    eventRepository.fetchEventCountFromActionEvents(companyId, action)

  }

  def getReportCountPercentage(
      user: Option[User],
      filter: ReportFilter,
      basePercentageFilter: ReportFilter
  ): Future[Int] =
    for {
      count     <- reportRepository.count(user, filter)
      baseCount <- reportRepository.count(user, basePercentageFilter)
    } yield toPercentage(count, baseCount)

  def getReportsCountCurve(
      user: Option[User],
      reportFilter: ReportFilter,
      ticks: Int = 12,
      tickDuration: CurveTickDuration = CurveTickDuration.Month
  ): Future[Seq[CountByDate]] =
    tickDuration match {
      case CurveTickDuration.Month => reportRepository.getMonthlyCount(user, reportFilter, ticks)
      case CurveTickDuration.Week  => reportRepository.getWeeklyCount(user, reportFilter, ticks)
      case CurveTickDuration.Day   => reportRepository.getDailyCount(user, reportFilter, ticks)
    }

  def getReportsTagsDistribution(companyId: Option[UUID], user: User): Future[Map[ReportTag, Int]] =
    reportRepository.getReportsTagsDistribution(companyId, user)

  def getReportsStatusDistribution(companyId: Option[UUID], user: User): Future[Map[String, Int]] =
    reportRepository.getReportsStatusDistribution(companyId, user)

  def getAcceptedResponsesDistribution(companyId: UUID, user: User): Future[Map[ExistingResponseDetails, Int]] =
    reportRepository.getAcceptedResponsesDistribution(companyId, user)

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

  def getResponseAvgDelay(companyId: UUID, userRole: UserRole): Future[Option[Duration]] = {
    val onlyProShareable = userRole == UserRole.Professionnel
    eventRepository.getAvgTimeUntilEvent(
      action = ActionEvent.REPORT_PRO_RESPONSE,
      companyId = Some(companyId),
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

  private[orchestrators] def buildCSV(
      arbo: List[ArborescenceNode],
      results: Seq[(String, List[String], Int, Int)]
  ): List[(String, String, Int, Int)] = {
    val merged = results.map { case (cat, subcat, count, reclamations) => (cat :: subcat, count, reclamations) }

    arbo.map { arborescenceNode =>
      val res          = merged.find(_._1 == arborescenceNode.path.map(_._1.key).toList)
      val count        = res.map(_._2).getOrElse(0)
      val reclamations = res.map(_._3).getOrElse(0)
      (
        s"\"${arborescenceNode.path.head._1.label.replace("\"", "\"\"")}\"",
        arborescenceNode.path.tail.map(_._1.label).map(_.replace("\"", "\"\"")).mkString("\"", ";", "\""),
        count,
        reclamations
      )
    }
  }

  private[orchestrators] def buildReportNodes(
      labels: List[SubcategoryLabel],
      locale: Locale,
      arbo: List[ArborescenceNode],
      results: Seq[(String, List[String], Int, Int)]
  ): List[ReportNode] = {
    val merged = results.map { case (cat, subcat, count, reclamations) => (cat :: subcat, count, reclamations) }
    val tree   = ReportNode("", "", None, 0, 0, List.empty, List.empty, None)

    arbo.foreach { arborescenceNode =>
      val res          = merged.find(_._1 == arborescenceNode.path.map(_._1.key).toList)
      val count        = res.map(_._2).getOrElse(0)
      val reclamations = res.map(_._3).getOrElse(0)
      createOrUpdateReportNode(arborescenceNode.overriddenCategory, arborescenceNode.path, count, reclamations, tree)
    }

    val arboPathes = arbo.map(_.path.map(_._1.key).toList)
    merged.foreach { case (path, count, reclamations) =>
      if (!arboPathes.contains(path))
        createOrUpdateReportNodeOld(labels, locale, List.empty, path, count, reclamations, tree)
    }

    tree.children
  }

  @tailrec
  private def createOrUpdateReportNode(
      overriddenCategory: Option[CategoryInfo],
      subcats: Vector[(CategoryInfo, NodeInfo)],
      count: Int,
      reclamations: Int,
      tree: ReportNode
  ): Unit = {
    tree.count += count
    tree.reclamations += reclamations
    subcats match {
      case (path, nodeInfo) +: rest =>
        tree.children.find(_.name == path.key) match {
          case Some(child) => createOrUpdateReportNode(overriddenCategory, rest, count, reclamations, child)
          case None =>
            val reportNode = ReportNode(
              path.key,
              path.label,
              overriddenCategory.map(_.label),
              0,
              0,
              List.empty,
              nodeInfo.tags,
              Some(nodeInfo.id)
            )
            tree.children = reportNode :: tree.children
            createOrUpdateReportNode(overriddenCategory, rest, count, reclamations, reportNode)

        }
      case _ => ()
    }
  }

  @tailrec
  private def createOrUpdateReportNodeOld(
      labels: List[SubcategoryLabel],
      locale: Locale,
      currentPath: List[String],
      paths: List[String],
      count: Int,
      reclamations: Int,
      tree: ReportNode
  ): Unit = {
    tree.count += count
    tree.reclamations += reclamations
    paths match {
      case path :: rest =>
        tree.children.find(_.name == path) match {
          case Some(child) =>
            createOrUpdateReportNodeOld(labels, locale, currentPath :+ path, rest, count, reclamations, child)
          case None =>
            val fullPath = currentPath :+ path
            val cat      = fullPath.headOption.getOrElse(path)
            val subcats  = fullPath.drop(1)

            val subcategoryLabel = labels
              .find(label => label.category == cat && label.subcategories == subcats)

            val label = subcategoryLabel match {
              case Some(SubcategoryLabel(c, Nil, clfr, clen, _, _)) =>
                (if (locale == Locale.FRENCH) clfr else clen).getOrElse(c)
              case Some(SubcategoryLabel(_, sl, _, _, slfr, slen)) =>
                (if (locale == Locale.FRENCH) slfr else slen).flatMap(_.lastOption).getOrElse(sl.last)
              case None => path
            }

            val reportNode = ReportNode(path, label, None, 0, 0, List.empty, List.empty, None)
            tree.children = reportNode :: tree.children
            createOrUpdateReportNodeOld(labels, locale, fullPath, rest, count, reclamations, reportNode)

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
