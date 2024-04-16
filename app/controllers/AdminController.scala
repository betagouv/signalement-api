package controllers

import authentication.Authenticator
import authentication.actions.UserAction.WithRole
import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import models._
import models.admin.ReportInputList
import models.event.Event
import models.report._
import orchestrators.ReportFileOrchestrator
import play.api.Logger
import play.api.i18n.Lang
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.twirl.api.Html
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.subscription.SubscriptionRepositoryInterface
import services.PDFService
import services.emails.Email._
import services.emails.EmailDefinitionsAdmin.AdminAccessLink
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.EmailDefinitionsConsumer.ConsumerReportAcknowledgment
import services.emails.EmailDefinitionsConsumer.ConsumerReportDeletionConfirmation
import services.emails.EmailDefinitionsDggcrf._
import services.emails.EmailDefinitionsPro._
import services.emails.EmailDefinitionsVarious.ResetPassword
import services.emails.EmailDefinitionsVarious.UpdateEmailAddress
import services.emails.EmailsExamplesUtils._
import services.emails.Email
import services.emails.EmailDefinition
import services.emails.MailService
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils._

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AdminController(
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService,
    pdfService: PDFService,
    emailConfiguration: EmailConfiguration,
    reportFileOrchestrator: ReportFileOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    subscriptionRepository: SubscriptionRepositoryInterface,
    implicit val frontRoute: FrontRoute,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger                        = Logger(this.getClass)
  implicit val contactAddress: EmailAddress = emailConfiguration.contactAddress
  implicit val timeout: akka.util.Timeout   = 5.seconds

  implicit val messagesProvider: Messages = MessagesImpl(Lang(Locale.FRENCH), controllerComponents.messagesApi)
  val availablePdfs = Seq[(String, Html)](
    "accountActivation" -> views.html.pdfs.accountActivation(
      genCompany,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "accountActivationReminder" -> views.html.pdfs.accountActivationReminder(
      genCompany,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "accountActivationLastReminder" -> views.html.pdfs.accountActivationLastReminder(
      genCompany,
      reportNumber = 33,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "report" -> views.html.pdfs.report(
      genReport,
      Some(genCompany),
      Nil,
      None,
      None,
      Nil,
      Nil
    )(frontRoute = frontRoute, None, messagesProvider),
    "proResponse" -> views.html.pdfs.proResponse(
      Some(
        Event(
          UUID.randomUUID(),
          Some(genReport.id),
          genReport.companyId,
          Some(genUser.id),
          OffsetDateTime.now(),
          EventType.PRO,
          ActionEvent.REPORT_PRO_RESPONSE,
          Json.toJson(genReportResponse)
        )
      )
    )(messagesProvider)
  )

  val newList: Seq[(String, (EmailAddress, MessagesApi) => Email)] = List(
    ResetPassword,
    UpdateEmailAddress,
    AdminAccessLink,
    AdminProbeTriggered,
    DgccrfAgentAccessLink,
    DgccrfInactiveAccount,
    DgccrfDangerousProductReportNotification,
    DgccrfReportNotification,
    DgccrfValidateEmail,
    ProCompanyAccessInvitation,
    ProCompaniesAccessesInvitations,
    ProNewCompanyAccess,
    ProNewCompaniesAccesses,
    ProResponseAcknowledgment,
    ProResponseAcknowledgmentOnAdminCompletion,
    ProNewReportNotification,
    ProReportReOpeningNotification,
    ProReportsReadReminder,
    ProReportsUnreadReminder,
    ProReportAssignedNotification,
    ConsumerReportDeletionConfirmation,
    ConsumerReportAcknowledgment
  ).flatMap(readExamplesWithFullKey)

  val availableEmails = List[(String, EmailAddress => Email)](
    // ======= PRO =======

    // ======= CONSO =======

    "consumer.report_transmitted" -> (recipient =>
      ConsumerReportReadByProNotification(
        genReport.copy(email = recipient),
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_ack_pro_consumer" -> (recipient =>
      ConsumerProResponseNotification(
        genReport.copy(email = recipient),
        genReportResponse,
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_ack_pro_consumer_on_admin_completion" -> (recipient =>
      ConsumerProResponseNotificationOnAdminCompletion(
        genReport.copy(email = recipient),
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_closed_no_reading" -> (recipient =>
      ConsumerReportClosedNoReading(
        genReport.copy(email = recipient),
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_closed_no_reading_case_dispute" ->
      (recipient =>
        ConsumerReportClosedNoReading(
          genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
          Some(genCompany),
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_closed_no_action" -> (recipient =>
      ConsumerReportClosedNoAction(
        genReport.copy(email = recipient),
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_closed_no_action_case_dispute" -> (recipient =>
      ConsumerReportClosedNoAction(
        genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
        Some(genCompany),
        controllerComponents.messagesApi
      )
    ),
    "consumer.validate_email" -> (recipient =>
      ConsumerValidateEmail(EmailValidation(email = recipient), None, controllerComponents.messagesApi)
    )
  )

  def getEmailCodes = SecuredAction.andThen(WithRole(UserRole.Admin)).async { _ =>
    val codesFromNewList = newList.map(_._1)
    Future(Ok(Json.toJson(availableEmails.map(_._1) ++ codesFromNewList)))
  }
  def sendTestEmail(templateRef: String, to: String) = SecuredAction.andThen(WithRole(UserRole.Admin)).async { _ =>
    val recipientAddress = EmailAddress(to)
    val itemInNewList    = newList.find(_._1 == templateRef)
    val maybeEmail = itemInNewList match {
      case Some(definition) =>
        Some(definition._2(recipientAddress, controllerComponents.messagesApi))
      case None =>
        availableEmails
          .find(_._1 == templateRef)
          .map { case (_, fn) => fn(recipientAddress) }
    }
    maybeEmail match {
      case None =>
        Future.successful(NotFound)
      case Some(email) =>
        mailService.send(email).map(_ => Ok)
    }
  }

  private def readExamplesWithFullKey(
      emailDefinition: EmailDefinition
  ): Seq[(String, (EmailAddress, MessagesApi) => Email)] =
    emailDefinition.examples.map { case (key, fn) =>
      s"${emailDefinition.category.toString.toLowerCase}.$key" -> fn
    }

  def getPdfCodes = SecuredAction.andThen(WithRole(UserRole.Admin)).async { _ =>
    Future(Ok(Json.toJson(availablePdfs.map(_._1))))
  }
  def sendTestPdf(templateRef: String) = SecuredAction.andThen(WithRole(UserRole.Admin)) { _ =>
    availablePdfs.toMap
      .get(templateRef)
      .map { html =>
        val pdfSource = pdfService.createPdfSource(Seq(html))
        Ok.chunked(
          content = pdfSource,
          inline = false,
          fileName = Some(s"${templateRef}_${OffsetDateTime.now().toString}.pdf")
        )
      }
      .getOrElse(NotFound)
  }

  private def _sendProAckToConsumer(reports: List[Report]) =
    for {
      eventsMap <- eventRepository.fetchEventsOfReports(reports)
      companies <- companyRepository.fetchCompanies(reports.flatMap(_.companyId))
      filteredEvents = reports.flatMap { report =>
        eventsMap
          .get(report.id)
          .flatMap(_.find(_.action == REPORT_PRO_RESPONSE))
          .map(evt => (report, evt))
      }

      _ <- filteredEvents.map { case (report, responseEvent) =>
        val maybeCompany = report.companyId.flatMap(companyId => companies.find(_.id == companyId))
        mailService.send(
          ConsumerProResponseNotification(
            report,
            responseEvent.details.as[ReportResponse],
            maybeCompany,
            controllerComponents.messagesApi
          )
        )
      }.sequence
    } yield ()

  def sendProAckToConsumer = SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportInputList](Json.reads[ReportInputList])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        results =>
          for {
            reports <- reportRepository.getReportsByIds(results.reportIds)
            _       <- _sendProAckToConsumer(reports)
          } yield Ok
      )
  }

  private def _sendNewReportToPro(reports: List[Report]) = {
    val reportAndCompanyIdList = reports.flatMap(report => report.companyId.map(c => (report, c)))
    for {
      reportAndEmailList <- reportAndCompanyIdList.map { case (report, companyId) =>
        companyAccessRepository
          .fetchAdmins(companyId)
          .map(_.map(_.email).distinct)
          .map(emails => (report, NonEmptyList.fromList(emails)))
      }.sequence
      _ <- reportAndEmailList.map {
        case (report, Some(adminsEmails)) =>
          mailService.send(ProNewReportNotification.build(adminsEmails, report))
        case (report, None) =>
          logger.debug(s"Not sending email for report ${report.id}, no admin found")
          Future.unit
      }.sequence
    } yield ()
  }

  def sendNewReportToPro = SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    for {
      reportInputList <- request.parseBody[ReportInputList]()
      reports         <- reportRepository.getReportsByIds(reportInputList.reportIds)
      _               <- _sendNewReportToPro(reports)
    } yield Ok
  }

  private def _sendReportAckToConsumer(reportsMap: Map[Report, List[ReportFile]]) = {
    val reports = reportsMap.keys.toList
    for {
      events    <- eventRepository.fetchEventsOfReports(reports)
      companies <- companyRepository.fetchCompanies(reports.flatMap(_.companyId))

      emailsToSend = reportsMap.flatMap { case (report, reportAttachements) =>
        val maybeCompany = report.companyId.flatMap(companyId => companies.find(_.id == companyId))
        val event =
          events.get(report.id).flatMap(_.find(_.action == Constants.ActionEvent.EMAIL_CONSUMER_ACKNOWLEDGMENT))

        event match {
          case Some(evt) =>
            Some(
              ConsumerReportAcknowledgment.EmailImpl(
                report,
                maybeCompany,
                evt,
                reportAttachements,
                controllerComponents.messagesApi
              )
            )
          case None =>
            logger.debug(s"Not sending email for report ${report.id}, no event found")
            None
        }
      }
      _ <- emailsToSend.toList.map(mailService.send).sequence
    } yield ()
  }

  def sendReportAckToConsumer = SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    logger.debug(s"Calling sendNewReportAckToConsumer to send back report ack email to consumers")
    for {
      reportInputList <- request.parseBody[ReportInputList]()
      reports         <- reportRepository.getReportsByIds(reportInputList.reportIds)
      reportFiles     <- reportFileOrchestrator.prefetchReportsFiles(reports.map(_.id))
      _ <- _sendReportAckToConsumer(reports.map(report => (report, reportFiles.getOrElse(report.id, List.empty))).toMap)
    } yield Ok

  }

  private def notifyDgccrfIfNeeded(report: Report): Future[Unit] = for {
    ddEmails <-
      if (report.tags.contains(ReportTag.ProduitDangereux)) {
        report.companyAddress.postalCode
          .map(postalCode => subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2)))
          .getOrElse(Future(Seq()))
      } else Future(Seq())
    _ <-
      if (ddEmails.nonEmpty) {
        mailService.send(DgccrfDangerousProductReportNotification.build(ddEmails, report))
      } else {
        Future.unit
      }
  } yield ()

  def resend(start: OffsetDateTime, end: OffsetDateTime, emailType: ResendEmailType) =
    SecuredAction.andThen(WithRole(UserRole.Admin)).async { implicit request =>
      for {
        reports <- reportRepository.getReportsWithFiles(
          Some(request.identity.userRole),
          ReportFilter(start = Some(start), end = Some(end))
        )
        _ <- emailType match {
          case ResendEmailType.NewReportAckToConsumer => _sendReportAckToConsumer(reports.toMap)
          case ResendEmailType.NewReportAckToPro      => _sendNewReportToPro(reports.keys.toList)
          case ResendEmailType.NotifyDGCCRF           => Future.sequence(reports.keys.map(notifyDgccrfIfNeeded))
          case ResendEmailType.ReportProResponse      => _sendProAckToConsumer(reports.keys.toList)
        }
      } yield NoContent
    }
}
