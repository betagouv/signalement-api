package actors

import akka.actor._
import akka.stream.Materializer
import play.api.Configuration
import play.api.Logger

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.mailer._
import services.MailerService
import utils.EmailAddress
import scala.concurrent.duration._

object EmailActor {
  def props = Props[EmailActor]

  case class EmailRequest(
      from: EmailAddress,
      recipients: Seq[EmailAddress],
      subject: String,
      bodyHtml: String,
      blindRecipients: Seq[EmailAddress] = Seq.empty,
      attachments: Seq[Attachment] = Seq.empty,
      times: Int = 0
  )
}

@Singleton
class EmailActor @Inject() (configuration: Configuration, mailerService: MailerService)(implicit val mat: Materializer)
    extends Actor {
  import EmailActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  override def receive = {
    case req: EmailRequest =>
      try {
        mailerService.sendEmail(
          req.from,
          req.recipients,
          req.blindRecipients,
          req.subject,
          req.bodyHtml,
          req.attachments
        )
        logger.debug(s"Sent email to ${req.recipients}")
      } catch {
        case e: Exception =>
          logger.error(e.getMessage, e)
          if (req.times < 2) {
            context.system.scheduler.scheduleOnce(req.times * 9 + 1 minute, self, req.copy(times = req.times + 1))
          }
      }
    case _ => logger.debug("Could not handle request")
  }
}

class EmailActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[EmailActor]("email-actor")
}
