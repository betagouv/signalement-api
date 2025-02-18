package services.emails

import cats.data.NonEmptyList
import config.EmailConfiguration
import play.api.Logger
import play.api.libs.mailer.Attachment
import repositories.reportblockednotification.ReportNotificationBlockedRepositoryInterface
import services.emails.MailRetriesService.EmailRequest
import services.AttachmentService
import services.PDFService
import utils.EmailAddress
import utils.FrontRoute

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MailService(
    mailRetriesService: MailRetriesService,
    emailConfiguration: EmailConfiguration,
    reportNotificationBlocklistRepo: ReportNotificationBlockedRepositoryInterface,
    val pdfService: PDFService,
    attachmentService: AttachmentService
)(implicit
    val frontRoute: FrontRoute,
    private[this] val executionContext: ExecutionContext
) extends MailServiceInterface {

  private[this] val logger                                = Logger(this.getClass)
  private[this] val mailFrom                              = emailConfiguration.from
  implicit private[this] val contactAddress: EmailAddress = emailConfiguration.contactAddress

  override def send(
      email: BaseEmail
  ): Future[Unit] = email match {
    case email: ProFilteredEmail => filterBlockedAndSend(email)
    case _ =>
      send(
        email.recipients,
        email.subject,
        email.getBody(frontRoute, contactAddress),
        email.getAttachements(attachmentService)
      )
  }

  /** Filter pro user recipients that are excluded from notifications and send email
    */
  private def filterBlockedAndSend(email: ProFilteredEmail): Future[Unit] = {
    val maybeCompanyId = email match {
      case email: ProFilteredEmailSingleReport   => email.report.companyId
      case email: ProFilteredEmailMultipleReport => email.reports.headOption.flatMap(_.companyId)
    }

    maybeCompanyId match {
      case Some(companyId) =>
        reportNotificationBlocklistRepo
          .filterBlockedEmails(email.recipients, companyId)
          .flatMap(recipient =>
            send(
              recipient.toList,
              email.subject,
              email.getBody(frontRoute, contactAddress),
              email.getAttachements(attachmentService)
            )
          )
      case None =>
        logger.debug("No company linked to report, not sending emails")
        Future.unit
    }
  }

  private def filterEmail(recipients: Seq[EmailAddress]): Seq[EmailAddress] =
    recipients.filter(_.nonEmpty).filter { emailAddress =>
      val isAllowed = emailConfiguration.outboundEmailFilterRegex.findFirstIn(emailAddress.value).nonEmpty
      if (!isAllowed) {
        logger.warn(
          s"""Filtering email ${emailAddress}
             |because it does not match outboundEmailFilterRegex conf pattern :
             | ${emailConfiguration.outboundEmailFilterRegex
              .toString()}""".stripMargin
        )
      }
      isAllowed
    }

  private def send(
      recipients: Seq[EmailAddress],
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment]
  ): Future[Unit] = {
    val filteredEmptyEmail: Seq[EmailAddress] = filterEmail(recipients)
    NonEmptyList.fromList(filteredEmptyEmail.toList) match {
      case None => ()
      case Some(filteredRecipients) =>
        filteredRecipients.grouped(emailConfiguration.maxRecipientsPerEmail).foreach { groupedRecipients =>
          val emailRequest = EmailRequest(
            from = mailFrom,
            recipients = groupedRecipients,
            subject = subject,
            bodyHtml = bodyHtml,
            attachments = attachments
          )
          // we launch this but don't wait for its completion
          mailRetriesService.sendEmailWithRetries(emailRequest)
        }
    }
    Future.unit
  }

}
