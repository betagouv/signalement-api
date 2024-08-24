package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError
import models.User
import models.event.Event
import models.report.Report
import models.report.reportmetadata.ReportComment
import models.report.reportmetadata.ReportWithMetadata
import play.api.Logger
import play.api.libs.json.Json
import repositories.event.EventRepositoryInterface
import repositories.reportmetadata.ReportMetadataRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsPro.ProReportAssignedNotification
import services.emails.MailService
import utils.Constants

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportAssignmentOrchestrator(
    reportOrchestrator: ReportOrchestrator,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    mailService: MailService,
    reportMetadataRepository: ReportMetadataRepositoryInterface,
    userRepository: UserRepositoryInterface,
    eventRepository: EventRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {
  val logger = Logger(this.getClass)

  def assignReportToUser(
      reportId: UUID,
      assigningUser: User,
      newAssignedUserId: UUID,
      reportComment: ReportComment
  ): Future[User] = {
    val assigningToSelf = assigningUser.id == newAssignedUserId
    for {
      maybeReportWithMetadata <- reportOrchestrator.getVisibleReportForUser(reportId, assigningUser)
      reportWithMetadata      <- maybeReportWithMetadata.liftTo[Future](AppError.ReportNotFound(reportId))
      newAssignedUser         <- checkAssignableToUser(reportWithMetadata, newAssignedUserId)
      _                       <- reportMetadataRepository.setAssignedUser(reportId, newAssignedUserId)
      _ <- createAssignmentEvent(reportWithMetadata.report, assigningUser, newAssignedUser, reportComment)
      _ <-
        if (assigningToSelf) Future.unit
        else
          mailService.send(
            ProReportAssignedNotification.Email(reportWithMetadata.report, assigningUser, newAssignedUser)
          )
    } yield newAssignedUser
  }

  private def checkAssignableToUser(
      reportWithMetadata: ReportWithMetadata,
      newAssignedUserId: UUID
  ): Future[User] = {
    val reportId               = reportWithMetadata.report.id
    val isAlreadyAssignedToHim = reportWithMetadata.metadata.flatMap(_.assignedUserId).contains(newAssignedUserId)
    for {
      _ <-
        if (isAlreadyAssignedToHim)
          Future.failed(AppError.AssignReportError(s"${reportId} is already assigned to ${newAssignedUserId}"))
        else Future.unit
      maybeUser <- userRepository.get(newAssignedUserId)
      user      <- maybeUser.liftTo[Future](AppError.AssignReportError(s"User ${newAssignedUserId} doesn't exist"))
      visibleCompanies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(user)
      visibleSirets = visibleCompanies.map(_.company.siret)
      isVisible     = reportWithMetadata.report.companySiret.exists(visibleSirets.contains)
      _ <-
        if (isVisible) Future.unit
        else Future.failed(AppError.AssignReportError(s"${reportId} can't be seen by user ${newAssignedUserId}"))
    } yield user
  }

  private def createAssignmentEvent(
      report: Report,
      assigningUser: User,
      assignedUser: User,
      reportComment: ReportComment
  ) =
    eventRepository
      .create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(assigningUser.id),
          OffsetDateTime.now(),
          Constants.EventType.PRO,
          Constants.ActionEvent.REPORT_ASSIGNED,
          formatEventDesc(assigningUser, assignedUser, reportComment)
        )
      )

  private def formatEventDesc(assigningUser: User, assignedUser: User, reportComment: ReportComment) =
    Json.obj(
      "description" -> s"Assignation du signalement à ${assignedUser.fullName} (${assignedUser.email})"
    ) ++ reportComment.comment.filterNot(_.isBlank).map(c => Json.obj("comment" -> c)).getOrElse(Json.obj())

}
