package orchestrators

import cats.implicits.catsSyntaxOption
import cats.implicits.catsSyntaxOptionId
import cats.implicits.toTraverseOps
import controllers.error.AppError
import controllers.error.AppError.CannotReopenReport
import controllers.error.AppError.SpamReportDeletionLimitedToUniqueSiren
import io.scalaland.chimney.dsl._
import models._
import models.company.Company
import models.event.Event
import models.report.ReportStatus.Transmis
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
import services.emails.EmailDefinitionsConsumer.ConsumerProResponseNotificationOnAdminCompletion
import services.emails.EmailDefinitionsConsumer.ConsumerReportDeletionConfirmation
import services.emails.EmailDefinitionsPro.ProReportReOpeningNotification
import services.emails.EmailDefinitionsPro.ProResponseAcknowledgmentOnAdminCompletion
import services.emails.MailService
import utils.Constants
import utils.Constants.ActionEvent._
import utils.Logs.RichLogger
import utils.SIREN.fromSIRET

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.tools.nsc.tasty.SafeEq

class ReportAdminActionOrchestrator(
    mailService: MailService,
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    engagementOrchestrator: EngagementOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportFileOrchestrator: ReportFileOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  private def fetchStrict(reportId: UUID): Future[Report] =
    reportRepository.get(reportId).flatMap(_.liftTo[Future](AppError.ReportNotFound(reportId)))

  def reportReOpening(reportId: UUID, user: User): Future[Unit] =
    for {
      report <- fetchStrict(reportId)
      isReopenable = report.status === ReportStatus.NonConsulte || report.status === ReportStatus.ConsulteIgnore
      _             <- if (isReopenable) Future.unit else throw CannotReopenReport
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
      _ <- users.traverse(u => mailService.send(ProReportReOpeningNotification.Email(u.map(_.email), updatedReport)))
    } yield ()

  private def reOpenReport(report: Report): Future[Report] = {
    val now = OffsetDateTime.now()
    val reOpenedReport = report.copy(
      expirationDate = now.plusDays(3),
      status = Transmis,
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
          deleteReportFromConsumerRequest(id, report, user, CONSUMER_THREATEN_BY_PRO, reportAdminCompletionDetails)
        case ReportAdminActionType.RefundBlackMail =>
          deleteReportFromConsumerRequest(id, report, user, REFUND_BLACKMAIL, reportAdminCompletionDetails)
        case ReportAdminActionType.OtherReasonDeleteRequest =>
          deleteReportFromConsumerRequest(id, report, user, OTHER_REASON_DELETE_REQUEST, reportAdminCompletionDetails)
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

  private def createAdminSpamDeletionReportEvent(
      maybeCompanyId: Option[UUID],
      user: User,
      event: ActionEventValue,
      count: Int
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
        Json.obj("Nombre de signalements concernés" -> count)
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

  private def deleteReportFromConsumerRequest(
      id: UUID,
      report: Report,
      user: User,
      event: ActionEventValue,
      reportAdminCompletionDetails: ReportAdminCompletionDetails
  ) =
    for {
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      _            <- deleteReport(id)
      _            <- createAdminDeletionReportEvent(report.companyId, user, event, reportAdminCompletionDetails)
      _            <- mailService.send(ConsumerReportDeletionConfirmation.Email(report, maybeCompany, messagesApi))
    } yield report

  def deleteSpammedReport(
      reportIds: Seq[UUID],
      user: User
  ): Future[List[UUID]] = {
    val uniqueReportIds = reportIds.distinct
    for {
      reports <- reportRepository.getReportsByIds(uniqueReportIds.toList)
      diff = uniqueReportIds.diff(reports.map(_.id))
      _    = logger.debug(s"Unknown reports : $diff")
      _ <-
        if (diff.nonEmpty) {
          Future.failed(AppError.ReportsNotFound(diff))
        } else Future.unit
      sirens = reports.flatMap(_.companySiret).map(fromSIRET).distinctBy(_.value)
      companies <- sirens.distinctBy(_.value) match {
        case siren :: Nil =>
          companyRepository
            .findBySiren(List(siren))

        case Nil => Future.successful(List.empty)
        case _   => Future.failed(SpamReportDeletionLimitedToUniqueSiren)
      }
      _ = logger
        .infoWithTitle(
          "spam_report_deletion",
          s"Removing ${reports.size} reports for companies ${companies.map(c => s" ${c.siret}").mkString(", ")} "
        )

      _ <- uniqueReportIds.traverse(deleteReport)
      _ <- companies.traverse(c => createAdminSpamDeletionReportEvent(c.id.some, user, REPORT_SPAM, reports.size))
    } yield reports.map(_.id)
  }

  def deleteReport(
      id: UUID
  ) =
    for {
      _ <- engagementOrchestrator.removeEngagement(id)
      _ <- eventRepository.deleteByReportId(id)
      _ <- reportFileOrchestrator.removeFromReportId(id)
      _ <- reportConsumerReviewOrchestrator.remove(id)
      _ <- reportRepository.delete(id)
    } yield ()

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
      _ <- users.traverse(u => mailService.send(ProResponseAcknowledgmentOnAdminCompletion.Email(report, u)))
      _ <- mailService.send(
        ConsumerProResponseNotificationOnAdminCompletion.Email(report, maybeCompany, messagesApi)
      )
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
