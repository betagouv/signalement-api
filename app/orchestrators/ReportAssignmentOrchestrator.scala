package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError
import models.User
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.reportmetadata.ReportWithMetadata
import play.api.Logger
import repositories.event.EventRepositoryInterface
import repositories.reportmetadata.ReportMetadataRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.Email.ProReportAssignedNotification
import services.MailService
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

  def assignReportToUser(reportId: UUID, assigningUser: User, newAssignedUserId: UUID): Future[ReportWithMetadata] = {
    val assigningToSelf = assigningUser.id == newAssignedUserId
    for {
      maybeReportWithMetadata <- reportOrchestrator.getVisibleReportForUser(reportId, assigningUser)
      reportWithMetadata      <- maybeReportWithMetadata.liftTo[Future](AppError.ReportNotFound(reportId))
      newAssignedUser         <- checkAssignableToUser(reportWithMetadata, newAssignedUserId)
      updatedMetadata         <- reportMetadataRepository.setAssignedUser(reportId, newAssignedUserId)
      updatedReportWithMetadata = reportWithMetadata.copy(metadata = Some(updatedMetadata))
      _ <- createAssignmentEvent(reportWithMetadata.report, assigningUser, newAssignedUser)
      _ <-
        if (assigningToSelf) Future.unit
        else mailService.send(ProReportAssignedNotification(reportWithMetadata.report, assigningUser, newAssignedUser))
    } yield updatedReportWithMetadata
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

  private def createAssignmentEvent(report: Report, assigningUser: User, assignedUser: User) =
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
          stringToDetailsJsValue(
            s"Affectation du signalement à ${assignedUser.firstName} ${assignedUser.lastName} (${assignedUser.email})"
          )
        )
      )

}
