package actors

import akka.actor._
import akka.stream.Materializer
import com.google.inject.AbstractModule
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.mailer._
import services.MailerService
import utils.EmailAddress

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object EmailActor {
  def props = Props[EmailActor]()

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
class EmailActor @Inject() (mailerService: MailerService)(implicit val mat: Materializer) extends Actor {
  import EmailActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.error(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
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
        sender() ! Status.Success
      } catch {
        case e: Exception =>
          logger.error(s"Unexpected error when sending email from request (number of attempt ${req.times + 1})", e)
          if (req.times < 2) {
            logger.debug(s"Failed attempt, rescheduling email")
            context.system.scheduler.scheduleOnce(req.times * 9 + 1 minute, self, req.copy(times = req.times + 1))
            logger.debug(s"Rescheduling done.")
            sender() ! Status.Success
          } else {
            sender() ! Status.Failure(e)
          }
      }
    case _ =>
      if (sender() != self) {
        logger.debug("Could not handle request, ignoring message")
        sender() ! Status.Success
      }
  }
}

class EmailActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[EmailActor]("email-actor")
}
