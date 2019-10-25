package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.Logger
import play.api.libs.mailer._
import utils.EmailAddress


class MailerService @Inject() (mailerClient: MailerClient, system: ActorSystem) {

  val logger: Logger = Logger(this.getClass)

  def sendEmail(from: EmailAddress, recipients: EmailAddress*)(subject: String, bodyHtml: String, attachments: Seq[Attachment] = Seq.empty) = {
    mailerClient.send(Email(subject, from.value, recipients.map(_.value), bodyHtml = Some(bodyHtml), attachments = attachments))
  }
}

