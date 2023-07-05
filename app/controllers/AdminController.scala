package controllers

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import com.mohiva.play.silhouette.api.Silhouette
import config.EmailConfiguration
import models.report.DetailInputValue.toDetailInputValue
import models._
import models.admin.ReportInputList
import models.auth.AuthToken
import models.company.Address
import models.company.Company
import models.event.Event
import models.report.ReportFileOrigin.CONSUMER
import models.report.reportfile.ReportFileId
import models.report.Gender
import models.report.Report
import models.report.ReportFile
import models.report.ReportResponse
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.ReportTag
import models.report.WebsiteURL
import orchestrators.ReportFileOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email.ConsumerProResponseNotification
import services.Email.ConsumerReportAcknowledgment
import services.Email.ConsumerReportClosedNoAction
import services.Email.ConsumerReportClosedNoReading
import services.Email.ConsumerReportReadByProNotification
import services.Email.DgccrfAccessLink
import services.Email.DgccrfDangerousProductReportNotification
import services.Email.DgccrfReportNotification
import services.Email.InactiveDgccrfAccount
import services.Email.ProCompanyAccessInvitation
import services.Email.ProNewCompanyAccess
import services.Email.ProNewReportNotification
import services.Email.ProReportReadReminder
import services.Email.ProReportUnreadReminder
import services.Email.ProResponseAcknowledgment
import services.Email.ResetPassword
import services.Email.ValidateEmail
import services.Email
import services.MailService
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.EventType
import utils._
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AdminController(
    val silhouette: Silhouette[AuthEnv],
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService,
    emailConfiguration: EmailConfiguration,
    reportFileOrchestrator: ReportFileOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    implicit val frontRoute: FrontRoute,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)
  implicit val contactAddress = emailConfiguration.contactAddress
  implicit val timeout: akka.util.Timeout = 5.seconds

  val dummyURL = java.net.URI.create("https://lien-test")

  private def genReport = Report(
    id = UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c"),
    gender = Some(Gender.Female),
    category = "Test",
    subcategories = List("test"),
    details = List(toDetailInputValue("test")),
    companyId = Some(UUID.randomUUID()),
    companyName = Some("Dummy Inc."),
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
    visibleToPro = true
  )

  private def genReportFile = ReportFile(
    id = ReportFileId.generateId(),
    reportId = Some(UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c")),
    creationDate = OffsetDateTime.now(),
    filename = s"${UUID.randomUUID.toString}.png",
    storageFilename = "String",
    origin = CONSUMER,
    avOutput = None
  )

  private def genReportResponse = ReportResponse(
    responseType = ReportResponseType.ACCEPTED,
    consumerDetails = "",
    dgccrfDetails = Some(""),
    fileIds = Nil
  )

  private def genCompany = Company(
    id = UUID.randomUUID,
    siret = SIRET.fromUnsafe("123456789"),
    creationDate = OffsetDateTime.now(),
    name = "Test Entreprise",
    address = Address(
      number = Some("3"),
      street = Some("rue des Champs"),
      postalCode = Some("75015"),
      city = Some("Paris")
    ),
    activityCode = None,
    isHeadOffice = true,
    isOpen = true,
    isPublic = true
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
      EventType.CONSO,
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

  val availableEmails = Map[String, EmailAddress => Email](
    "dgccrf.inactive_account_reminder" -> (recipient =>
      InactiveDgccrfAccount(genUser.copy(email = recipient), Some(LocalDate.now().plusDays(90)))
    ),
    "dgccrf.reset_password" -> (recipient => ResetPassword(genUser.copy(email = recipient), genAuthToken)),
    "pro.access_invitation" -> (recipient => ProCompanyAccessInvitation(recipient, genCompany, dummyURL, None)),
    "pro.new_company_access" -> (recipient => ProNewCompanyAccess(recipient, genCompany, None)),
    "pro.report_ack_pro" -> (recipient =>
      ProResponseAcknowledgment(genReport, genReportResponse, genUser.copy(email = recipient))
    ),
    "pro.report_notification" -> (recipient => ProNewReportNotification(NonEmptyList.of(recipient), genReport)),
    "pro.report_transmitted_reminder" -> (recipient =>
      ProReportReadReminder(
        List(recipient),
        genReport,
        OffsetDateTime.now().plusDays(10)
      )
    ),
    "pro.report_unread_reminder" -> (recipient =>
      ProReportUnreadReminder(
        List(recipient),
        genReport,
        OffsetDateTime.now().plusDays(10)
      )
    ),
    "dgccrf.access_link" ->
      (DgccrfAccessLink(_, frontRoute.dashboard.Dgccrf.register(token = "abc"))),
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
      ConsumerReportAcknowledgment(genReport.copy(email = recipient), Some(genCompany), genEvent, Nil)
    ),
    "consumer.report_ack_case_reponseconso" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(status = ReportStatus.NA, tags = List(ReportTag.ReponseConso), email = recipient),
          Some(genCompany),
          genEvent,
          Nil
        )
      ),
    "consumer.report_ack_case_dispute" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(tags = List(ReportTag.LitigeContractuel), email = recipient),
          Some(genCompany),
          genEvent,
          Nil
        )
      ),
    "consumer.report_ack_case_dangerous_product" ->
      (recipient =>
        ConsumerReportAcknowledgment(
          genReport.copy(
            status = ReportStatus.NA,
            tags = List(ReportTag.ProduitDangereux),
            email = recipient
          ),
          Some(genCompany),
          genEvent,
          Nil
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
          Nil
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
          Nil
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
          Nil
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
          Nil
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
          Nil
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
        Nil
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
          Nil
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
          Nil
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
        Nil
      )
    ),
    "consumer.report_transmitted" -> (recipient =>
      ConsumerReportReadByProNotification(genReport.copy(email = recipient), Some(genCompany))
    ),
    "consumer.report_ack_pro_consumer" -> (recipient =>
      ConsumerProResponseNotification(genReport.copy(email = recipient), genReportResponse, Some(genCompany))
    ),
    "consumer.report_closed_no_reading" -> (recipient =>
      ConsumerReportClosedNoReading(genReport.copy(email = recipient), Some(genCompany))
    ),
    "consumer.report_closed_no_reading_case_dispute" ->
      (recipient =>
        ConsumerReportClosedNoReading(
          genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
          Some(genCompany)
        )
      ),
    "consumer.report_closed_no_action" -> (recipient =>
      ConsumerReportClosedNoAction(genReport.copy(email = recipient), Some(genCompany))
    ),
    "consumer.report_closed_no_action_case_dispute" -> (recipient =>
      ConsumerReportClosedNoAction(
        genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
        Some(genCompany)
      )
    )
  )

  def getEmailCodes = SecuredAction(WithRole(UserRole.Admin)).async { _ =>
    Future(Ok(Json.toJson(availableEmails.keys)))
  }
  def sendTestEmail(templateRef: String, to: String) = SecuredAction(WithRole(UserRole.Admin)).async { _ =>
    Future(
      availableEmails
        .get(templateRef)
        .map(e => mailService.send(e(EmailAddress(to))))
        .map(_ => Ok)
        .getOrElse(NotFound)
    )
  }

  def sendProAckToConsumer = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportInputList](Json.reads[ReportInputList])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        results =>
          for {
            reports <- reportRepository.getReportsByIds(results.reportIds)
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
                ConsumerProResponseNotification(report, responseEvent.details.as[ReportResponse], maybeCompany)
              )
            }.sequence
          } yield Ok
      )
  }

  def sendNewReportToPro = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    for {
      reportInputList <- request.parseBody[ReportInputList]()
      reportIds <- reportRepository.getReportsByIds(reportInputList.reportIds)
      reportAndCompanyIdList = reportIds.flatMap(report => report.companyId.map(c => (report, c)))
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
    } yield Ok

  }

  def sendReportAckToConsumer = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    logger.debug(s"Calling sendNewReportAckToConsumer to send back report ack email to consumers")
    for {
      reportInputList <- request.parseBody[ReportInputList]()
      reports <- reportRepository.getReportsByIds(reportInputList.reportIds)
      reportFiles <- reportFileOrchestrator.prefetchReportsFiles(reportInputList.reportIds)
      events <- eventRepository.fetchEventsOfReports(reports)
      companies <- companyRepository.fetchCompanies(reports.flatMap(_.companyId))

      emailsToSend = reports.flatMap { report =>
        val maybeCompany = report.companyId.flatMap(companyId => companies.find(_.id == companyId))
        val event =
          events.get(report.id).flatMap(_.find(_.action == Constants.ActionEvent.EMAIL_CONSUMER_ACKNOWLEDGMENT))
        val reportAttachements = reportFiles.getOrElse(report.id, List.empty)

        event match {
          case Some(evt) => Some(ConsumerReportAcknowledgment(report, maybeCompany, evt, reportAttachements))
          case None =>
            logger.debug(s"Not sending email for report ${report.id}, no event found")
            None
        }
      }
      _ <- emailsToSend.map(mailService.send).sequence
    } yield Ok

  }
}
