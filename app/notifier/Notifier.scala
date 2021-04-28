package notifier

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import actors.{EmailActor, UploadActor}
import akka.actor.ActorRef
import akka.pattern.ask
import javax.inject.{Inject, Named}
import models.Event._
import models.ReportResponse._
import models._
import play.api.libs.json.Json
import play.api.libs.mailer.AttachmentData
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, PDFService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, EventType}
import utils.{Constants, EmailAddress, EmailSubjects, URL}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class Notifier @Inject()(
  reportRepository: ReportRepository,
  companyRepository: CompanyRepository,
  accessTokenRepository: AccessTokenRepository,
  eventRepository: EventRepository,
  websiteRepository: WebsiteRepository,
  emailValidationRepository: EmailValidationRepository,
  mailerService: MailerService,
  pdfService: PDFService,
  @Named("email-actor") emailActor: ActorRef,
  @Named("upload-actor") uploadActor: ActorRef,
  s3Service: S3Service,
  configuration: Configuration,
  environment: Environment)
  (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))

  implicit val timeout: akka.util.Timeout = 5.seconds

  private def notifyProfessionalOfNewReport(report: Report, company: Company): Future[Report] = {
    companyRepository.fetchAdmins(company).flatMap(admins => {
      if (admins.nonEmpty) {
        emailActor ? EmailActor.EmailRequest(
          from = mailFrom,
          recipients = admins.map(_.email),
          subject = EmailSubjects.NEW_REPORT,
          bodyHtml = views.html.mails.professional.reportNotification(report).toString
        )
        val user = admins.head // We must chose one as Event links to a single User
        eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(company.id),
            Some(user.id),
            Some(OffsetDateTime.now()),
            Constants.EventType.PRO,
            Constants.ActionEvent.EMAIL_PRO_NEW_REPORT,
            stringToDetailsJsValue(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${admins.map(_.email).mkString(", ")} )")
          )
        ).flatMap(event =>
          reportRepository.update(report.copy(status = TRAITEMENT_EN_COURS))
        )
      } else {
        genActivationToken(company, tokenDuration).map(_ => report)
      }
    })
  }

}
