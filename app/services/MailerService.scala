package services

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.Logger
import play.api.libs.mailer._

class MailerService @Inject() (mailerClient: MailerClient, system: ActorSystem) {

  val logger: Logger = Logger(this.getClass)

  def sendEmail(from: String, recipients: String*)(subject: String, bodyHtml: String, attachments: Seq[Attachment] = Seq.empty) = {
    mailerClient.send(Email(subject, from, recipients, bodyHtml = Some(bodyHtml), attachments = attachments))
  }

}

