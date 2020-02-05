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

  def sendEmail(from: EmailAddress, recipients: EmailAddress*)(subject: String, bodyHtml: String, attachments: Seq[Attachment] = defaultAttachments) = {
    mailerClient.send(Email(subject, from.value, recipients.map(_.value), bodyHtml = Some(bodyHtml), attachments = attachments))
  }
}

