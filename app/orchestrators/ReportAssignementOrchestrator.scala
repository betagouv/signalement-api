package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError
import models.User
import models.report.reportmetadata.ReportWithMetadata
import play.api.Logger
import repositories.report.ReportRepositoryInterface
import repositories.reportmetadata.ReportMetadataRepositoryInterface
import repositories.user.UserRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportAssignementOrchestrator(
    reportOrchestrator: ReportOrchestrator,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    reportMetadataRepository: ReportMetadataRepositoryInterface,
    userRepository: UserRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {
  val logger = Logger(this.getClass)

  def assignReportToUser(reportId: UUID, currentUser: User, newAssignedUserId: UUID): Future[Unit] =
    for {
      maybeReportWithMetadata <- reportOrchestrator.getVisibleReportForUser(reportId, currentUser)
      reportWithMetadata      <- maybeReportWithMetadata.liftTo[Future](AppError.ReportNotFound(reportId))
      _                       <- checkAssignableToUser(reportWithMetadata, newAssignedUserId)
      _                       <- reportMetadataRepository.setAssignedUser(reportId, newAssignedUserId)
    } yield ()

  private def checkAssignableToUser(
      reportWithMetadata: ReportWithMetadata,
      newAssignedUserId: UUID
  ): Future[Unit] = {
    val reportId               = reportWithMetadata.report.id
    val isAlreadyAssignedToHim = reportWithMetadata.metadata.flatMap(_.assignedUserId).contains(newAssignedUserId)
    for {
      _ <-
        if (isAlreadyAssignedToHim)
          Future.failed(AppError.AssignReportError(s"${reportId} is already assigned to ${newAssignedUserId}"))
        else Future.successful()
      maybeUser <- userRepository.get(newAssignedUserId)
      user      <- maybeUser.liftTo[Future](AppError.AssignReportError(s"User ${newAssignedUserId} doesn't exist"))
      visibleCompanies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(user)
      visibleSirets = visibleCompanies.map(_.company.siret)
      isVisible     = visibleSirets.contains(reportWithMetadata.report.companySiret)
      _ <-
        if (isVisible) Future.successful()
        else Future.failed(AppError.AssignReportError(s"${reportId} can't be seen by user ${newAssignedUserId}"))
    } yield ()
  }

}
