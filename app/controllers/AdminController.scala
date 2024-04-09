package controllers

import authentication.Authenticator
import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import models.report.DetailInputValue.toDetailInputValue
import models._
import models.admin.ReportInputList
import models.auth.AuthToken
import models.company.Address
import models.company.Company
import models.event.Event
import models.report.ReportFileOrigin.Consumer
import models.report.reportfile.ReportFileId
import models.report.Gender
import models.report.Report
import models.report.ReportFile
import models.report.ReportFilter
import models.report.ReportResponse
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.ReportTag
import models.report.ResponseDetails
import models.report.WebsiteURL
import orchestrators.ReportFileOrchestrator
import play.api.Logger
import play.api.i18n.Lang
import play.api.i18n.Messages
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
import services.Email.AgentAccessLink
import services.Email.ConsumerProResponseNotification
import services.Email.ConsumerReportAcknowledgment
import services.Email.ConsumerReportClosedNoAction
import services.Email.ConsumerReportClosedNoReading
import services.Email.ConsumerReportReadByProNotification
import services.Email.ConsumerValidateEmail
import services.Email.DgccrfDangerousProductReportNotification
import services.Email.DgccrfReportNotification
import services.Email.InactiveDgccrfAccount
import services.Email.ProCompanyAccessInvitation
import services.Email.ProNewCompanyAccess
import services.Email.ProNewReportNotification
import services.Email.ProReportAssignedNotification
import services.Email.ProReportReOpeningNotification
import services.Email.ProReportsReadReminder
import services.Email.ProReportsUnreadReminder
import services.Email.ProResponseAcknowledgment
import services.Email.ResetPassword
import services.Email.ValidateEmail
import services.Email
import services.MailService
import services.PDFService
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils._
import authentication.actions.UserAction.WithRole

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
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

  val dummyURL = java.net.URI.create("https://lien-test")

  private def genReport = Report(
    id = UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c"),
    gender = Some(Gender.Female),
    category = "Test",
    subcategories = List("test"),
    details = List(toDetailInputValue("test")),
    companyId = Some(UUID.randomUUID()),
    companyName = Some("Dummy Inc."),
    companyBrand = Some("Dummy Inc. Store"),
    companyAddress = Address(Some("3 bis"), Some("Rue des exemples"), None, Some("13006"), Some("Douceville")),
    companySiret = Some(SIRET("12345678912345")),
    companyActivityCode = None,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    creationDate = OffsetDateTime.now(),
    firstName = "John",
    lastName = "Doe",
    email = EmailAddress("john.doe@example.com"),
    contactAgreement = true,
    employeeConsumer = false,
    status = ReportStatus.TraitementEnCours,
    expirationDate = OffsetDateTime.now().plusDays(50),
    influencer = None,
    visibleToPro = true,
    lang = Some(Locale.FRENCH),
    barcodeProductId = None,
    train = None,
    station = None
  )

  private def genReportFile = ReportFile(
    id = ReportFileId.generateId(),
    reportId = Some(UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c")),
    creationDate = OffsetDateTime.now(),
    filename = s"${UUID.randomUUID.toString}.png",
    storageFilename = "String",
    origin = Consumer,
    avOutput = None
  )

  private def genReportResponse = ReportResponse(
    responseType = ReportResponseType.ACCEPTED,
    responseDetails = Some(ResponseDetails.REFUND),
    otherResponseDetails = None,
    consumerDetails = "",
    dgccrfDetails = Some(""),
    fileIds = Nil
  )

  private def genCompany = Company(
    id = UUID.randomUUID,
    siret = SIRET.fromUnsafe("12345678901234"),
    creationDate = OffsetDateTime.now(),
    name = "Test Entreprise",
    address = Address(
      number = Some("3"),
      street = Some("rue des Champs"),
      postalCode = Some("75015"),
      city = Some("Paris"),
      country = None
    ),
    activityCode = None,
    isHeadOffice = true,
    isOpen = true,
    isPublic = true,
    brand = Some("une super enseigne")
  )

  private def genUser = User(
    id = UUID.randomUUID,
    password = "",
    email = EmailAddress("text@example.com"),
    firstName = "Jeanne",
    lastName = "Dupont",
    userRole = UserRole.Admin,
    lastEmailValidation = None
  )

  private def genEvent =
    Event(
      UUID.randomUUID(),
      None,
      None,
      None,
      OffsetDateTime.now(),
      EventType.PRO,
      POST_ACCOUNT_ACTIVATION_DOC
    )

  private def genAuthToken =
    AuthToken(UUID.randomUUID, UUID.randomUUID, OffsetDateTime.now().plusDays(10))

  private def genSubscription = Subscription(
    id = UUID.randomUUID,
    userId = None,
    email = None,
    departments = List("75"),
    countries = Nil,
    withTags = Nil,
    withoutTags = Nil,
    categories = Nil,
    sirets = Nil,
    frequency = java.time.Period.ofDays(1)
  )

  case class EmailContent(subject: String, body: play.twirl.api.Html)

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

  val availableEmails = Map[String, EmailAddress => Email](
    "dgccrf.inactive_account_reminder" -> (recipient =>
      InactiveDgccrfAccount(genUser.copy(email = recipient), Some(LocalDate.now().plusDays(90)))
    ),
    "various.reset_password" -> (recipient => ResetPassword(genUser.copy(email = recipient), genAuthToken)),
    "pro.access_invitation"  -> (recipient => ProCompanyAccessInvitation(recipient, genCompany, dummyURL, None)),
    "pro.new_company_access" -> (recipient => ProNewCompanyAccess(recipient, genCompany, None)),
    "pro.report_ack_pro" -> (recipient =>
      ProResponseAcknowledgment(genReport, genReportResponse, genUser.copy(email = recipient))
    ),
    "pro.report_notification" -> (recipient => ProNewReportNotification(NonEmptyList.of(recipient), genReport)),
    "pro.report_reopening_notification" -> (recipient => ProReportReOpeningNotification(List(recipient), genReport)),
    "pro.reports_transmitted_reminder" -> (recipient => {
      val report1 = genReport
      val report2 = genReport.copy(companyId = report1.companyId)
      val report3 = genReport.copy(companyId = report1.companyId, expirationDate = OffsetDateTime.now().plusDays(5))
      ProReportsReadReminder(
        List(recipient),
        List(report1, report2, report3),
        Period.ofDays(7)
      )
    }),
    "pro.reports_unread_reminder" -> (recipient => {
      val report1 = genReport
      val report2 = genReport.copy(companyId = report1.companyId)
      val report3 = genReport.copy(companyId = report1.companyId, expirationDate = OffsetDateTime.now().plusDays(5))

      ProReportsUnreadReminder(
        List(recipient),
        List(report1, report2, report3),
        Period.ofDays(7)
      )
    }),
    "pro.report_assignement_to_other" -> (recipient =>
      ProReportAssignedNotification(
        report = genReport,
        assigningUser = genUser,
        assignedUser = genUser.copy(email = recipient)
      )
    ),
    "dgccrf.access_link" ->
      (AgentAccessLink("DGCCRF")(_, frontRoute.dashboard.Agent.register(token = "abc"))),
    "dgccrf.validate_email" ->
      (ValidateEmail(_, 7, frontRoute.dashboard.validateEmail(""))),
    "dgccrf.report_dangerous_product_notification" -> (recipient =>
      DgccrfDangerousProductReportNotification(Seq(recipient), genReport)
    ),
    "dgccrf.report_notif_dgccrf" -> (recipient =>
      DgccrfReportNotification(
        List(recipient),
        genSubscription,
        List(
          (genReport, List(genReportFile)),
          (genReport.copy(tags = List(ReportTag.ReponseConso)), List(genReportFile))
        ),
        LocalDate.now().minusDays(10)
      )
    ),
    "consumer.report_ack" -> (recipient =>
      ConsumerReportAcknowledgment(
        genReport.copy(email = recipient),
        Some(genCompany),
        genEvent,
        Nil,
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_ack_case_reponseconso" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(status = ReportStatus.NA, tags = List(ReportTag.ReponseConso), email = recipient),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_dispute" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(tags = List(ReportTag.LitigeContractuel), email = recipient),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_dangerous_product" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.TraitementEnCours,
            tags = List(ReportTag.ProduitDangereux),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_euro" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            companyAddress = Address(country = Some(Country.Italie)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_euro_and_dispute" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            tags = List(ReportTag.LitigeContractuel),
            companyAddress = Address(country = Some(Country.Islande)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_andorre" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            companyAddress = Address(country = Some(Country.Andorre)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_andorre_and_dispute" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            tags = List(ReportTag.LitigeContractuel),
            companyAddress = Address(country = Some(Country.Andorre)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_suisse" ->

      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            companyAddress = Address(country = Some(Country.Suisse)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_suisse_and_dispute" -> (recipient =>
      ConsumerReportAcknowledgment(
        genReport.copy(
          status = ReportStatus.NA,
          tags = List(ReportTag.LitigeContractuel),
          companyAddress = Address(country = Some(Country.Suisse)),
          email = recipient
        ),
        Some(genCompany),
        genEvent,
        Nil,
        controllerComponents.messagesApi
      )
    ),
    "consumer.report_ack_case_compagnie_aerienne" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            email = recipient,
            tags = List(ReportTag.CompagnieAerienne)
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_abroad_default" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            companyAddress = Address(country = Some(Country.Bahamas)),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil,
          controllerComponents.messagesApi
        )
      ),
    "consumer.report_ack_case_abroad_default_and_dispute" -> (recipient =>
      ConsumerReportAcknowledgment(
        genReport.copy(
          status = ReportStatus.NA,
          tags = List(ReportTag.LitigeContractuel),
          companyAddress = Address(country = Some(Country.Bahamas)),
          email = recipient
        ),
        Some(genCompany),
        genEvent,
        Nil,
        controllerComponents.messagesApi
      )
    ),
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
    Future(Ok(Json.toJson(availableEmails.keys)))
  }
  def sendTestEmail(templateRef: String, to: String) = SecuredAction.andThen(WithRole(UserRole.Admin)).async { _ =>
    Future(
      availableEmails
        .get(templateRef)
        .map(e => mailService.send(e(EmailAddress(to))))
        .map(_ => Ok)
        .getOrElse(NotFound)
    )
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
          mailService.send(ProNewReportNotification(adminsEmails, report))
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
              ConsumerReportAcknowledgment(
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
        mailService.send(DgccrfDangerousProductReportNotification(ddEmails, report))
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
