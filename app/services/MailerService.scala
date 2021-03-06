package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.{Environment, Logger}
import play.api.libs.mailer._
import utils.EmailAddress


class MailerService @Inject() (mailerClient: MailerClient, system: ActorSystem, environment: Environment) {

  val logger: Logger = Logger(this.getClass)

  val defaultAttachments = Seq(
    AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo-signalconso")),
    AttachmentFile("logo-marianne.png", environment.getFile("/appfiles/logo-marianne.png"), contentId = Some("logo-marianne"))
  )

  def sendEmail(from: EmailAddress, recipients: Seq[EmailAddress], blindRecipients: Seq[EmailAddress] = Seq.empty, subject: String, bodyHtml: String, attachments: Seq[Attachment] = Seq.empty) = {
    mailerClient.send(Email(subject, from.value, recipients.map(_.value), bcc = blindRecipients.map(_.value), bodyHtml = Some(bodyHtml), attachments = defaultAttachments ++ attachments))
  }

  def attachmentSeqForWorkflowStepN(n: Int) = Seq(AttachmentFile(s"schemaSignalConso-Etape$n.png", environment.getFile(s"/appfiles/schemaSignalConso-Etape$n.png"), contentId = Some(s"schemaSignalConso-Etape$n")))
}

