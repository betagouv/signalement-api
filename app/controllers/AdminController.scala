package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import actors.EmailActor
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named, Singleton}
import models._
import play.api.{Configuration, Logger}
import repositories._
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}
import utils.{Address, EmailAddress, EmailSubjects, SIRET}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDate


@Singleton
class AdminController @Inject()(reportRepository: ReportRepository,
                                val configuration: Configuration,
                                val silhouette: Silhouette[AuthEnv],
                                @Named("email-actor") emailActor: ActorRef,
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
    details = List("test"),
    companyId = None,
    companyName = None,
    companyAddress = None,
    companyPostalCode = None,
    companySiret = None,
    websiteId = None,
    websiteURL = None,
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
    address = Address("3 rue des Champs 75015 Paris"),
    postalCode = Some("75015")
  )

  private def genUser = User(
    id = UUID.randomUUID,
    password = "",
    email = EmailAddress("text@example.com"),
    firstName = "Jeanne",
    lastName = "Dupont",
    userRole = UserRoles.Admin
  )

  private def genAuthToken = AuthToken(UUID.randomUUID, UUID.randomUUID, OffsetDateTime.now.plusDays(10))

  private def genSubscription = Subscription(
    id = UUID.randomUUID,
    userId = None,
    email = None,
    departments = List("75"),
    categories = Nil,
    sirets = Nil,
    frequency = java.time.Period.ofDays(1)
  )

  case class EmailContent(subject: String, body: play.twirl.api.Html)

  private def genContent(templateRef: String): Option[EmailContent] = {
    templateRef match {
      case "reset_password" => Some(EmailContent(EmailSubjects.RESET_PASSWORD, views.html.mails.resetPassword(genUser, genAuthToken)))
      case "new_company_access" => {
        val company = genCompany
        Some(EmailContent(EmailSubjects.NEW_COMPANY_ACCESS(company.name), views.html.mails.professional.newCompanyAccessNotification(websiteUrl.resolve("/connexion"), company, None)))
      }
      case "pro_access_invitation" => {
        val company = genCompany
        Some(EmailContent(
          EmailSubjects.COMPANY_ACCESS_INVITATION(company.name),
          views.html.mails.professional.companyAccessInvitation(dummyURL, company, None)
        ))
      }
      case "dgccrf_access_link" => Some(EmailContent(EmailSubjects.DGCCRF_ACCESS_LINK, views.html.mails.dgccrf.accessLink(websiteUrl.resolve(s"/dgccrf/rejoindre/?token=abc"))))
      case "pro_report_notification" => Some(EmailContent(EmailSubjects.NEW_REPORT, views.html.mails.professional.reportNotification(genReport)))
      case "admin_report_notification" => {
        val report = genReport
        Some(EmailContent(EmailSubjects.ADMIN_NEW_REPORT(report.category), views.html.mails.professional.reportNotification(report)))
      }
      case "consumer_report_ack" => Some(EmailContent(EmailSubjects.REPORT_ACK, views.html.mails.consumer.reportAcknowledgment(genReport, Nil)))
      case "report_transmitted" => Some(EmailContent(EmailSubjects.REPORT_TRANSMITTED, views.html.mails.consumer.reportTransmission(genReport)))
      case "report_ack_pro" => Some(EmailContent(EmailSubjects.REPORT_ACK_PRO, views.html.mails.professional.reportAcknowledgmentPro(genReportResponse, genUser)))
      case "report_ack_pro_consumer" => Some(EmailContent(EmailSubjects.REPORT_ACK_PRO_CONSUMER, views.html.mails.consumer.reportToConsumerAcknowledgmentPro(genReport, genReportResponse, websiteUrl.resolve(s"/suivi-des-signalements/abc/avis"))))
      case "report_ack_pro_admin" => Some(EmailContent(EmailSubjects.REPORT_ACK_PRO_ADMIN("test cat"), views.html.mails.admin.reportToAdminAcknowledgmentPro(genReport, genReportResponse)))
      case "report_unread_reminder" => Some(EmailContent(EmailSubjects.REPORT_UNREAD_REMINDER, views.html.mails.professional.reportUnreadReminder(genReport, OffsetDateTime.now.plusDays(10))))
      case "report_transmitted_reminder" => Some(EmailContent(EmailSubjects.REPORT_TRANSMITTED_REMINDER, views.html.mails.professional.reportTransmittedReminder(genReport, OffsetDateTime.now.plusDays(10))))
      case "report_closed_no_reading" => Some(EmailContent(EmailSubjects.REPORT_CLOSED_NO_READING, views.html.mails.consumer.reportClosedByNoReading(genReport)))
      case "report_closed_no_action" => Some(EmailContent(EmailSubjects.REPORT_CLOSED_NO_ACTION, views.html.mails.consumer.reportClosedByNoAction(genReport)))
      case "report_notif_dgccrf" => Some(EmailContent(EmailSubjects.REPORT_NOTIF_DGCCRF(1), views.html.mails.dgccrf.reportNotification(genSubscription, List(genReport), LocalDate.now.minusDays(10))))
      case _ => None
    }
  }

  def sendTestEmail(templateRef: String, to: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    Future(genContent(templateRef).map{case EmailContent(subject, body) =>
      emailActor ? EmailActor.EmailRequest(
          from = mailFrom,
          recipients = Seq(EmailAddress(to)),
          subject = subject,
          bodyHtml = body.toString
      )
    }.map(_ => Ok).getOrElse(NotFound))
  }
}
