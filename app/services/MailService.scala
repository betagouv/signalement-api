package services

import actors.EmailActor
import akka.actor.ActorRef
import akka.pattern.ask
import models.EmailValidation
import models.Report
import play.api.Configuration
import play.api.Logger
import play.api.mvc.Request
import utils.EmailAddress
import utils.EmailSubjects

import java.net.URI
import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MailService @Inject() (
    @Named("email-actor") emailActor: ActorRef,
    configuration: Configuration
)(implicit
    private[this] val executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)
  private[this] val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  private[this] val tokenDuration =
    configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  implicit private[this] val websiteUrl = configuration.get[URI]("play.website.url")
  implicit private[this] val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")
  implicit private[this] val ccrfEmailSuffix = configuration.get[String]("play.mail.ccrfEmailSuffix")
  implicit private[this] val timeout: akka.util.Timeout = 5.seconds

  def sendConsumerEmailConfirmation(email: EmailValidation)(implicit request: Request[Any]) =
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = Seq(email.email),
      subject = EmailSubjects.VALIDATE_EMAIL,
      bodyHtml = views.html.mails.consumer.confirmEmail(email.email, email.confirmationCode).toString
    )

  def sendDangerousProductEmail(emails: Seq[EmailAddress], report: Report) =
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = emails,
      subject = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] ")),
      bodyHtml = views.html.mails.dgccrf.reportDangerousProductNotification(report).toString
    )
}
