package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError
import models.User
import models.event.Event
import models.report.Report
import models.report.ReportWithMetadataAndAlbertLabel
import models.report.reportmetadata.ReportComment
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
    visibleReportOrchestrator: VisibleReportOrchestrator,
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
      maybeReportExtra <- visibleReportOrchestrator.getVisibleReportForUserWithExtra(reportId, assigningUser)
      reportExtra      <- maybeReportExtra.liftTo[Future](AppError.ReportNotFound(reportId))
      newAssignedUser  <- checkAssignableToUser(reportExtra, newAssignedUserId)
      _                <- reportMetadataRepository.setAssignedUser(reportId, newAssignedUserId)
      _                <- createAssignmentEvent(reportExtra.report, assigningUser, newAssignedUser, reportComment)
      _ <-
        if (assigningToSelf) Future.unit
        else
          mailService.send(
            ProReportAssignedNotification.Email(
              reportExtra.report,
              assigningUser,
              newAssignedUser,
              reportComment.comment
            )
          )
    } yield newAssignedUser
  }

  private def checkAssignableToUser(
      reportExtra: ReportWithMetadataAndAlbertLabel,
      newAssignedUserId: UUID
  ): Future[User] = {
    val reportId               = reportExtra.report.id
    val isAlreadyAssignedToHim = reportExtra.metadata.flatMap(_.assignedUserId).contains(newAssignedUserId)
    for {
      _ <-
        if (isAlreadyAssignedToHim)
          Future.failed(AppError.AssignReportError(s"${reportId} is already assigned to ${newAssignedUserId}"))
        else Future.unit
      maybeUser <- userRepository.get(newAssignedUserId)
      user      <- maybeUser.liftTo[Future](AppError.AssignReportError(s"User ${newAssignedUserId} doesn't exist"))
      visibleCompanies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(user)
      visibleSirets = visibleCompanies.map(_.company.siret)
      isVisible     = reportExtra.report.companySiret.exists(visibleSirets.contains)
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
          formatEventDesc(assignedUser, reportComment)
        )
      )

  private def formatEventDesc(assignedUser: User, reportComment: ReportComment) =
    Json.obj(
      "description" -> s"Affectation du signalement à ${assignedUser.fullName} (${assignedUser.email})"
    ) ++ reportComment.comment.filterNot(_.isBlank).map(c => Json.obj("comment" -> c)).getOrElse(Json.obj())

}
