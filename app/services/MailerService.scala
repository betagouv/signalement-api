package services

import play.api.Logger
import play.api.libs.mailer._
import utils.EmailAddress

import javax.inject.Inject

class MailerService @Inject() (mailerClient: MailerClient) {

  val logger: Logger = Logger(this.getClass)

  def sendEmail(
      from: EmailAddress,
      recipients: Seq[EmailAddress],
      blindRecipients: Seq[EmailAddress] = Seq.empty,
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment] = Seq.empty
  ): String =
    mailerClient.send(
      Email(
        subject,
        from.value,
        recipients.map(_.value),
        bcc = blindRecipients.map(_.value),
        bodyHtml = Some(bodyHtml),
        attachments = attachments
      )
    )
}
