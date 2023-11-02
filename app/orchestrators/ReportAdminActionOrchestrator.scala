package orchestrators

import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError
import controllers.error.AppError.CannotReopenReport
import io.scalaland.chimney.dsl.TransformerOps
import models._
import models.company.Company
import models.event.Event
import models.report.ReportStatus.TraitementEnCours
import models.report._
import models.report.delete.ReportAdminAction
import models.report.delete.ReportAdminActionType
import models.report.delete.ReportAdminCompletionDetails
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email._
import services.MailService
import utils.Constants
import utils.Constants.ActionEvent._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.tools.nsc.tasty.SafeEq

class ReportAdminActionOrchestrator(
    mailService: MailService,
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    reportFileOrchestrator: ReportFileOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  private def fetchStrict(reportId: UUID): Future[Report] =
    reportRepository.get(reportId).flatMap(_.liftTo[Future](AppError.ReportNotFound(reportId)))

  def reportReOpening(reportId: UUID, user: User): Future[Unit] =
    for {
      report <- fetchStrict(reportId)
      isReopenable = report.status === ReportStatus.NonConsulte || report.status === ReportStatus.ConsulteIgnore
      _             <- if (isReopenable) Future(()) else throw CannotReopenReport
      updatedReport <- reOpenReport(report)
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(reportId),
          report.companyId,
          Some(user.id),
          OffsetDateTime.now(),
          Constants.EventType.ADMIN,
          REPORT_REOPENED_BY_ADMIN
        )
      )
      (_, users) <- getCompanyWithUsers(updatedReport)
      _          <- users.traverse(u => mailService.send(ProReportReOpeningNotification(u.map(_.email), updatedReport)))
    } yield ()

  private def reOpenReport(report: Report): Future[Report] = {
    val now = OffsetDateTime.now()
    val reOpenedReport = report.copy(
      expirationDate = now.plusDays(3),
      status = TraitementEnCours,
      reopenDate = Some(now)
    )
    reportRepository.update(report.id, reOpenedReport)
  }

  def reportDeletion(id: UUID, reason: ReportAdminAction, user: User): Future[Report] =
    fetchStrict(id).flatMap { report =>
      val reportAdminCompletionDetails =
        report.into[ReportAdminCompletionDetails].withFieldConst(_.comment, reason.comment).transform

      reason.reportAdminActionType match {
        case ReportAdminActionType.SolvedContractualDispute =>
          handleAdminReportCompletion(report, reportAdminCompletionDetails, user)
        case ReportAdminActionType.ConsumerThreatenByPro =>
          deleteReport(id, report, user, CONSUMER_THREATEN_BY_PRO, reportAdminCompletionDetails)
        case ReportAdminActionType.RefundBlackMail =>
          deleteReport(id, report, user, REFUND_BLACKMAIL, reportAdminCompletionDetails)
        case ReportAdminActionType.RGPDRequest =>
          deleteReport(id, report, user, RGPD_REQUEST, reportAdminCompletionDetails)
      }
    }

  private def createAdminDeletionReportEvent(
      maybeCompanyId: Option[UUID],
      user: User,
      event: ActionEventValue,
      reportAdminCompletionDetails: ReportAdminCompletionDetails
  ) =
    eventRepository.create(
      Event(
        UUID.randomUUID(),
        None,
        maybeCompanyId,
        Some(user.id),
        OffsetDateTime.now(),
        Constants.EventType.ADMIN,
        event,
        Json.toJson(reportAdminCompletionDetails)
      )
    )

  private def createAdminCompletionReportEvent(
      reportId: UUID,
      maybeCompanyId: Option[UUID],
      user: User,
      event: ActionEventValue,
      reportAdminCompletionDetails: ReportAdminCompletionDetails
  ) =
    eventRepository.create(
      Event(
        UUID.randomUUID(),
        Some(reportId),
        maybeCompanyId,
        Some(user.id),
        OffsetDateTime.now(),
        Constants.EventType.ADMIN,
        event,
        Json.toJson(reportAdminCompletionDetails)
      )
    )

  private def deleteReport(
      id: UUID,
      report: Report,
      user: User,
      event: ActionEventValue,
      reportAdminCompletionDetails: ReportAdminCompletionDetails
  ) =
    for {
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret(_)).flatSequence
      _            <- eventRepository.deleteByReportId(id)
      _            <- reportFileOrchestrator.removeFromReportId(id)
      _            <- reportConsumerReviewOrchestrator.remove(id)
      _            <- reportRepository.delete(id)
      _ <- report.companyId.map(id => reportOrchestrator.removeAccessTokenWhenNoMoreReports(id)).getOrElse(Future(()))
      _ <- createAdminDeletionReportEvent(report.companyId, user, event, reportAdminCompletionDetails)
      _ <- mailService.send(ReportDeletionConfirmation(report, maybeCompany, messagesApi))
    } yield report

  private def getCompanyWithUsers(report: Report): Future[(Option[Company], Option[List[User]])] = for {
    maybeCompany <- report.companySiret.map(companyRepository.findBySiret(_)).flatSequence
    users        <- maybeCompany.traverse(c => companiesVisibilityOrchestrator.fetchUsersByCompany(c.id))
  } yield (maybeCompany, users)

  private def handleAdminReportCompletion(
      report: Report,
      reportAdminCompletionDetails: ReportAdminCompletionDetails,
      user: User
  ): Future[Report] =
    for {
      _ <- reportRepository.update(
        report.id,
        report.copy(
          status = ReportStatus.PromesseAction
        )
      )
      _ <- createAdminCompletionReportEvent(
        report.id,
        report.companyId,
        user,
        SOLVED_CONTRACTUAL_DISPUTE,
        reportAdminCompletionDetails
      )
      (maybeCompany, users) <- getCompanyWithUsers(report)
      _ <- users.traverse(u => mailService.send(ProResponseAcknowledgmentOnAdminCompletion(report, u)))
      _ <- mailService.send(ConsumerProResponseNotificationOnAdminCompletion(report, maybeCompany, messagesApi))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
        )
      )
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(user.id),
          OffsetDateTime.now(),
          Constants.EventType.PRO,
          Constants.ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
        )
      )
    } yield report

}
