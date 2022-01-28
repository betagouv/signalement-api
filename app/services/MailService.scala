package services

import actors.EmailActor.EmailRequest
import akka.actor.ActorRef
import akka.pattern.ask
import cats.data.NonEmptyList
import config.EmailConfiguration
import play.api.Logger
import play.api.libs.mailer.Attachment
import repositories.ReportNotificationBlockedRepository
import utils.EmailAddress
import utils.FrontRoute

import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class MailService @Inject() (
    @Named("email-actor") actor: ActorRef,
    emailConfiguration: EmailConfiguration,
    reportNotificationBlocklistRepo: ReportNotificationBlockedRepository,
    implicit val frontRoute: FrontRoute,
    val pdfService: PDFService,
    attachementService: AttachementService
)(implicit
    private[this] val executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)
  private[this] val mailFrom = emailConfiguration.from
  implicit private[this] val contactAddress = emailConfiguration.contactAddress
  implicit private[this] val timeout: akka.util.Timeout = 5.seconds

  def send(
      email: Email
  ): Future[Unit] = email match {
    case email: ProFilteredEmail => filterBlockedAndSend(email)
    case email =>
      send(
        email.recipients,
        email.subject,
        email.getBody(frontRoute, contactAddress),
        email.getAttachements(attachementService)
      )
  }

  /** Filter pro user recipients that are excluded from notifications and send email
    */
  private def filterBlockedAndSend(email: ProFilteredEmail): Future[Unit] =
    email.report.companyId match {
      case Some(companyId) =>
        reportNotificationBlocklistRepo
          .filterBlockedEmails(email.recipients, companyId)
          .flatMap(recipient =>
            send(
              recipient.toList,
              email.subject,
              email.getBody(frontRoute, contactAddress),
              email.getAttachements(attachementService)
            )
          )
      case None =>
        logger.debug("No company linked to report, not sending emails")
        Future.successful(())
    }

  private def send(
      recipients: Seq[EmailAddress],
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment]
  ): Future[Unit] = {
    val filteredEmptyEmail: Seq[EmailAddress] = recipients.filter(_.nonEmpty)
    NonEmptyList.fromList(filteredEmptyEmail.toList) match {
      case None =>
        Future.successful(())
      case Some(filteredRecipients) =>
        val emailRequest = EmailRequest(
          from = mailFrom,
          recipients = filteredRecipients,
          subject = subject,
          bodyHtml = bodyHtml,
          attachments = attachments
        )

        (actor ? emailRequest).map(_ => ()).recoverWith { case err =>
          logger.error(
            s"Unexpected error when sending email request to mail actor [from :${emailRequest.from},recipients: ${emailRequest.recipients},subject : ${emailRequest.subject}]",
            err
          )

          Future.failed(err)
        }
    }
  }

}
