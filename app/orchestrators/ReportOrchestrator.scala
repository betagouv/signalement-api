package orchestrators

import config.SignalConsoConfiguration
import controllers.error.AppError._
import models._
import models.company.Company
import models.event.Event
import models.report._
import models.report.reportmetadata.ReportWithMetadata
import play.api.Logger
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.user.UserRepositoryInterface
import utils.Constants.ActionEvent

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReportOrchestrator(
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportFileOrchestrator: ReportFileOrchestrator,
    eventRepository: EventRepositoryInterface,
    userRepository: UserRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    signalConsoConfiguration: SignalConsoConfiguration
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def createFakeReportForBlacklistedUser(draftReport: ReportDraft): Report = {
    val maybeCompanyId     = draftReport.companySiret.map(_ => UUID.randomUUID())
    val reportCreationDate = OffsetDateTime.now()
    val expirationDate     = chooseExpirationDate(baseDate = reportCreationDate, None)
    draftReport.generateReport(maybeCompanyId, None, reportCreationDate, expirationDate)
  }

  private def chooseExpirationDate(
      baseDate: OffsetDateTime,
      maybeCompanyWithUsers: Option[(Company, List[User])]
  ): OffsetDateTime = {
    val delayIfCompanyHasNoUsers = Period.ofDays(60)
    val delayIfCompanyHasUsers   = Period.ofDays(25)
    val delay =
      if (maybeCompanyWithUsers.exists(_._2.nonEmpty)) delayIfCompanyHasUsers
      else delayIfCompanyHasNoUsers
    baseDate.plus(delay)
  }

  def getReportsForUser(
      connectedUser: User,
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[ReportWithFiles]] =
    for {
      sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
        filter.siretSirenList,
        connectedUser
      )
      _ = logger.trace(
        s"Original sirenSirets : ${filter.siretSirenList} , SanitizedSirenSirets : $sanitizedSirenSirets"
      )
      paginatedReportFiles <-
        if (sanitizedSirenSirets.isEmpty && connectedUser.userRole == UserRole.Professionnel) {
          Future(PaginatedResult(totalCount = 0, hasNextPage = false, entities = List.empty[ReportWithFiles]))
        } else {
          getReportsWithFile[ReportWithFiles](
            Some(connectedUser.userRole),
            filter.copy(siretSirenList = sanitizedSirenSirets),
            offset,
            limit,
            (r: ReportWithMetadata, m: Map[UUID, List[ReportFile]]) =>
              ReportWithFiles(r.report, r.metadata, m.getOrElse(r.report.id, Nil))
          )
        }
    } yield paginatedReportFiles

  def getReportsWithResponsesForUser(
      connectedUser: User,
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[ReportWithFilesAndResponses]] = {

    val filterByReportProResponse = EventFilter(None, Some(ActionEvent.REPORT_PRO_RESPONSE))
    for {
      reportsWithFiles <- getReportsForUser(connectedUser, filter, offset, limit)

      reports   = reportsWithFiles.entities.map(_.report)
      reportsId = reports.map(_.id)

      reportEventsMap <- eventRepository
        .getEventsWithUsers(reportsId, filterByReportProResponse)
        .map(events =>
          events.collect { case (event @ Event(_, Some(reportId), _, _, _, _, _, _), user) =>
            (reportId, EventWithUser(event, user))
          }.toMap
        )

      assignedUsersIds = reportsWithFiles.entities.flatMap(_.metadata.flatMap(_.assignedUserId))
      assignedUsers      <- userRepository.findByIds(assignedUsersIds)
      consumerReviewsMap <- reportConsumerReviewOrchestrator.find(reportsId)
    } yield reportsWithFiles.copy(
      entities = reportsWithFiles.entities.map { reportWithFiles =>
        val maybeAssignedUserId = reportWithFiles.metadata.flatMap(_.assignedUserId)
        ReportWithFilesAndResponses(
          reportWithFiles.report,
          reportWithFiles.metadata,
          assignedUser = assignedUsers.find(u => maybeAssignedUserId.contains(u.id)).map(MinimalUser.fromUser),
          reportWithFiles.files,
          consumerReviewsMap.getOrElse(reportWithFiles.report.id, None),
          reportEventsMap.get(reportWithFiles.report.id)
        )
      }
    )
  }

  def getReportsWithFile[T](
      userRole: Option[UserRole],
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      toApi: (ReportWithMetadata, Map[UUID, List[ReportFile]]) => T
  ): Future[PaginatedResult[T]] = {

    val maxResults = signalConsoConfiguration.reportsExportLimitMax
    for {
      _ <- limit match {
        case Some(limitValue) if limitValue > maxResults =>
          logger.error(s"Max page size reached $limitValue > $maxResults")
          Future.failed(ExternalReportsMaxPageSizeExceeded(maxResults))
        case a => Future.successful(a)
      }
      validLimit      = limit.orElse(Some(maxResults))
      validOffset     = offset.orElse(Some(0L))
      startGetReports = System.nanoTime()
      _               = logger.trace("----------------  BEGIN  getReports  ------------------")
      paginatedReports <-
        reportRepository.getReports(
          userRole,
          filter,
          validOffset,
          validLimit
        )
      endGetReports = System.nanoTime()
      _ = logger.trace(
        s"----------------  END  getReports ${TimeUnit.MILLISECONDS.convert(endGetReports - startGetReports, TimeUnit.NANOSECONDS)}  ------------------"
      )
      startGetReportFiles = System.nanoTime()
      _                   = logger.trace("----------------  BEGIN  prefetchReportsFiles  ------------------")
      reportsIds          = paginatedReports.entities.map(_.report.id)
      reportFilesMap <- reportFileOrchestrator.prefetchReportsFiles(reportsIds)
      endGetReportFiles = System.nanoTime()
      _ = logger.trace(s"----------------  END  prefetchReportsFiles ${TimeUnit.MILLISECONDS
          .convert(endGetReportFiles - startGetReportFiles, TimeUnit.NANOSECONDS)}  ------------------")
    } yield paginatedReports.mapEntities(r => toApi(r, reportFilesMap))
  }

}
