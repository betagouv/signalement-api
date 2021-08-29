package controllers

import actors.EmailActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models.DetailInputValue.toDetailInputValue
import models._
import play.api.Configuration
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories._
import services.MailerService
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ReportStatus.NA
import utils.Constants.Tags
import utils._
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class AdminController @Inject() (
    reportRepository: ReportRepository,
    companyRepository: CompanyRepository,
    eventRepository: EventRepository,
    mailerService: MailerService,
    val configuration: Configuration,
    val silhouette: Silhouette[AuthEnv],
    @Named("email-actor") emailActor: ActorRef
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  implicit val timeout: akka.util.Timeout = 5.seconds

  val dummyURL = java.net.URI.create("https://lien-test")

  private def genReport = Report(
    id = UUID.randomUUID,
    category = "Test",
    subcategories = List("test"),
    details = List(toDetailInputValue("test")),
    companyId = None,
    companyName = None,
    companyAddress = Address(None, None, None, None),
    companySiret = None,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    creationDate = OffsetDateTime.now,
    firstName = "John",
    lastName = "Doe",
    email = EmailAddress("john.doe@example.com"),
    contactAgreement = true,
    employeeConsumer = false,
    status = utils.Constants.ReportStatus.TRAITEMENT_EN_COURS
  )

  private def genReportResponse = ReportResponse(
    responseType = ReportResponseType.ACCEPTED,
    consumerDetails = "",
    dgccrfDetails = Some(""),
    fileIds = Nil
  )

  private def genCompany = Company(
    id = UUID.randomUUID,
    siret = SIRET("123456789"),
    creationDate = OffsetDateTime.now,
    name = "Test Entreprise",
    address = Address(
      number = Some("3"),
      street = Some("rue des Champs"),
      postalCode = Some("75015"),
      city = Some("Paris")
    ),
    activityCode = None
  )

  private def genUser = User(
    id = UUID.randomUUID,
    password = "",
    email = EmailAddress("text@example.com"),
    firstName = "Jeanne",
    lastName = "Dupont",
    userRole = UserRoles.Admin,
    lastEmailValidation = None
  )

  private def genAuthToken = AuthToken(UUID.randomUUID, UUID.randomUUID, OffsetDateTime.now.plusDays(10))

  private def genSubscription = Subscription(
    id = UUID.randomUUID,
    userId = None,
    email = None,
    departments = List("75"),
    countries = Nil,
    tags = Nil,
    categories = Nil,
    sirets = Nil,
    frequency = java.time.Period.ofDays(1)
  )

  case class EmailContent(subject: String, body: play.twirl.api.Html)

  val availableEmails = Map[String, () => EmailContent](
    "reset_password" -> (() =>
      EmailContent(EmailSubjects.RESET_PASSWORD, views.html.mails.resetPassword(genUser, genAuthToken))
    ),
    "new_company_access" -> (() => {
      val company = genCompany
      EmailContent(
        EmailSubjects.NEW_COMPANY_ACCESS(company.name),
        views.html.mails.professional.newCompanyAccessNotification(websiteUrl.resolve("/connexion"), company, None)
      )
    }),
    "pro_access_invitation" -> (() => {
      val company = genCompany
      EmailContent(
        EmailSubjects.COMPANY_ACCESS_INVITATION(company.name),
        views.html.mails.professional.companyAccessInvitation(dummyURL, company, None)
      )
    }),
    "dgccrf_access_link" -> (() =>
      EmailContent(
        EmailSubjects.DGCCRF_ACCESS_LINK,
        views.html.mails.dgccrf.accessLink(websiteUrl.resolve(s"/dgccrf/rejoindre/?token=abc"))
      )
    ),
    "pro_report_notification" -> (() =>
      EmailContent(EmailSubjects.NEW_REPORT, views.html.mails.professional.reportNotification(genReport))
    ),
    "consumer_report_ack" -> (() =>
      EmailContent(EmailSubjects.REPORT_ACK, views.html.mails.consumer.reportAcknowledgment(genReport, Nil))
    ),
    "consumer_report_ack_case_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(genReport.copy(tags = List(Tags.ContractualDispute)), Nil)
      )
    ),
    "consumer_report_ack_case_euro" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(
          genReport.copy(status = NA, companyAddress = Address(country = Some(Country.Italie))),
          Nil
        )
      )
    ),
    "consumer_report_ack_case_euro_and_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(
          genReport.copy(
            status = NA,
            companyAddress = Address(country = Some(Country.Islande)),
            tags = List(Tags.ContractualDispute)
          ),
          Nil
        )
      )
    ),
    "consumer_report_ack_case_andorre" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer
          .reportAcknowledgment(genReport.copy(status = NA, companyAddress = Address(country = Some(Country.Andorre))))
      )
    ),
    "consumer_report_ack_case_andorre_and_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(
          genReport.copy(
            status = NA,
            companyAddress = Address(country = Some(Country.Andorre)),
            tags = List(Tags.ContractualDispute)
          )
        )
      )
    ),
    "consumer_report_ack_case_suisse" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer
          .reportAcknowledgment(genReport.copy(status = NA, companyAddress = Address(country = Some(Country.Suisse))))
      )
    ),
    "consumer_report_ack_case_suisse_and_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(
          genReport.copy(
            status = NA,
            companyAddress = Address(country = Some(Country.Suisse)),
            tags = List(Tags.ContractualDispute)
          )
        )
      )
    ),
    "consumer_report_ack_case_abroad_default" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer
          .reportAcknowledgment(genReport.copy(status = NA, companyAddress = Address(country = Some(Country.Bahamas))))
      )
    ),
    "consumer_report_ack_case_abroad_default_and_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK,
        views.html.mails.consumer.reportAcknowledgment(
          genReport.copy(
            status = NA,
            companyAddress = Address(country = Some(Country.Mexique)),
            tags = List(Tags.ContractualDispute)
          )
        )
      )
    ),
    "report_transmitted" -> (() =>
      EmailContent(EmailSubjects.REPORT_TRANSMITTED, views.html.mails.consumer.reportTransmission(genReport))
    ),
    "report_ack_pro" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK_PRO,
        views.html.mails.professional.reportAcknowledgmentPro(genReportResponse, genUser)
      )
    ),
    "report_ack_pro_consumer" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_ACK_PRO_CONSUMER,
        views.html.mails.consumer.reportToConsumerAcknowledgmentPro(
          genReport,
          genReportResponse,
          websiteUrl.resolve(s"/suivi-des-signalements/abc/avis")
        )
      )
    ),
    "report_unread_reminder" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_UNREAD_REMINDER,
        views.html.mails.professional.reportUnreadReminder(genReport, OffsetDateTime.now.plusDays(10))
      )
    ),
    "report_transmitted_reminder" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_TRANSMITTED_REMINDER,
        views.html.mails.professional.reportTransmittedReminder(genReport, OffsetDateTime.now.plusDays(10))
      )
    ),
    "report_closed_no_reading" -> (() =>
      EmailContent(EmailSubjects.REPORT_CLOSED_NO_READING, views.html.mails.consumer.reportClosedByNoReading(genReport))
    ),
    "report_closed_no_reading_case_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_CLOSED_NO_READING,
        views.html.mails.consumer.reportClosedByNoReading(genReport.copy(tags = List(Tags.ContractualDispute)))
      )
    ),
    "report_closed_no_action" -> (() =>
      EmailContent(EmailSubjects.REPORT_CLOSED_NO_ACTION, views.html.mails.consumer.reportClosedByNoAction(genReport))
    ),
    "report_closed_no_action_case_dispute" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_CLOSED_NO_ACTION,
        views.html.mails.consumer.reportClosedByNoAction(genReport.copy(tags = List(Tags.ContractualDispute)))
      )
    ),
    "report_notif_dgccrf" -> (() =>
      EmailContent(
        EmailSubjects.REPORT_NOTIF_DGCCRF(1, None),
        views.html.mails.dgccrf.reportNotification(genSubscription, List(genReport), LocalDate.now.minusDays(10))
      )
    )
  )

  def getEmailCodes = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    Future(Ok(Json.toJson(availableEmails.keys)))
  }
  def sendTestEmail(templateRef: String, to: String) = SecuredAction(WithRole(UserRoles.Admin)).async {
    implicit request =>
      Future(
        availableEmails
          .get(templateRef)
          .map(_.apply())
          .map { case EmailContent(subject, body) =>
            emailActor ? EmailActor.EmailRequest(
              from = mailFrom,
              recipients = Seq(EmailAddress(to)),
              subject = subject,
              bodyHtml = body.toString
            )
          }
          .map(_ => Ok)
          .getOrElse(NotFound)
      )
  }

  def sendProAckToConsumer = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    import AdminObjects.ReportList
    request.body
      .validate[ReportList](Json.reads[ReportList])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        results => {
          for {
            reports <- reportRepository.getReportsByIds(results.reportIds)
            eventsMap <- eventRepository.prefetchReportsEvents(reports)
          } yield reports.foreach { report =>
            eventsMap
              .get(report.id)
              .flatMap(_.find(_.action == REPORT_PRO_RESPONSE))
              .map { responseEvent =>
                emailActor ? EmailActor.EmailRequest(
                  from = mailFrom,
                  recipients = Seq(report.email),
                  subject = EmailSubjects.REPORT_ACK_PRO_CONSUMER,
                  bodyHtml = views.html.mails.consumer
                    .reportToConsumerAcknowledgmentPro(
                      report,
                      responseEvent.details.as[ReportResponse],
                      configuration.get[URI]("play.website.url").resolve(s"/suivi-des-signalements/${report.id}/avis")
                    )
                    .toString,
                  attachments = mailerService.attachmentSeqForWorkflowStepN(4)
                )
              }
          }
          Future(Ok)
        }
      )
  }

  def sendNewReportToPro = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    import AdminObjects.ReportList
    request.body
      .validate[ReportList](Json.reads[ReportList])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        results => {
          reportRepository
            .getReportsByIds(results.reportIds)
            .map(_.foreach { report =>
              report.companyId.map { companyId =>
                companyRepository.fetchAdmins(companyId).map(_.map(_.email).distinct).flatMap { adminsEmails =>
                  if (adminsEmails.nonEmpty) {
                    emailActor ? EmailActor.EmailRequest(
                      from = mailFrom,
                      recipients = adminsEmails,
                      subject = EmailSubjects.NEW_REPORT,
                      bodyHtml = views.html.mails.professional.reportNotification(report).toString
                    )
                  } else {
                    Future.successful(())
                  }
                }
              }
            })
          Future(Ok)
        }
      )
  }
}

object AdminObjects {
  case class ReportList(reportIds: List[UUID])
}
