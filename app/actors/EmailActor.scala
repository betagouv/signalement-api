package actors

import akka.actor._
import akka.stream.Materializer
import cats.data.NonEmptyList
import com.sun.mail.smtp.SMTPSendFailedException
import play.api.Logger
import play.api.libs.mailer._
import services.MailerService
import utils.EmailAddress
import utils.Logs.RichLogger

import javax.mail.internet.AddressException
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random
object EmailActor {
  def props = Props[EmailActor]()

  case class EmailRequest(
      from: EmailAddress,
      recipients: NonEmptyList[EmailAddress],
      subject: String,
      bodyHtml: String,
      blindRecipients: Seq[EmailAddress] = Seq.empty,
      attachments: Seq[Attachment] = Seq.empty,
      numAttempt: Int = 1
  )

  def getDelayBeforeNextRetry(nbPastAttempts: Int, withRandomJitter: Boolean = false): Option[FiniteDuration] =
    if (nbPastAttempts >= 7) None
    else {
      // 2s, then 16s, etc. See unit test for details
      val nbSeconds = 2 * Math.pow(nbPastAttempts.toDouble, 3)
      // Random jitter https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
      val finalNbSeconds = if (withRandomJitter) Random.between(0.8, 1.2) * nbSeconds else nbSeconds
      Some(
        finalNbSeconds.seconds
      )
    }
}

class EmailActor(mailerService: MailerService)(implicit val mat: Materializer) extends Actor {
  import EmailActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.error(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  override def receive = {
    case req: EmailRequest =>
      val numAttempt = req.numAttempt
      val logDetails =
        s"""(attempt #$numAttempt, to ${req.recipients.toList.mkString(", ")}, subject "${req.subject}")"""
      try {
        logger.infoWithTitle("email_sending_attempt", s"Sending email $logDetails")
        mailerService.sendEmail(
          req.from,
          req.recipients.toList,
          req.blindRecipients,
          req.subject,
          req.bodyHtml,
          req.attachments
        )
        logger.infoWithTitle("email_sent", s"Sent email $logDetails")
      } catch {
        case e: Exception if isCausedByAddressException(e) =>
          logger.warnWithTitle(
            "email_malformed_address",
            s"Malformed email address $logDetails"
          )
        case e: Exception if isCausedByUnexceptedRecipients(e) =>
          logger.warnWithTitle(
            "email_unexpected_recipients",
            s"Received unexpected recipients error $logDetails"
          )
        case e: Exception =>
          logger.errorWithTitle(
            "email_sending_failed",
            s"Unexpected error when sending email $logDetails",
            e
          )
          getDelayBeforeNextRetry(numAttempt, withRandomJitter = true) match {
            case Some(delay) =>
              context.system.scheduler.scheduleOnce(delay, self, req.copy(numAttempt = numAttempt + 1))
              ()
            case None =>
              logger.errorWithTitle(
                "email_max_delivery_attempts",
                s"Email has reached max delivery attempts, aborting delivery $logDetails"
              )
          }

      }

    case _ =>
      logger.error("Unknown message received by EmailActor")

  }

  private def isCausedByAddressException(e: Exception): Boolean =
    e.getCause match {
      case null                => false
      case _: AddressException => true
      case _                   => false
    }

  // This case happens when trying to send through Sendinblue to the email "......@gmail.com"
  // (with the dots exactly like that)
  // It seems we don't get an AddressException, but it's Sendinblue that answers in a weird way
  private def isCausedByUnexceptedRecipients(e: Exception): Boolean =
    e.getCause match {
      case null                           => false
      case cause: SMTPSendFailedException =>
        // The full message with "......@gmail.com" was "400 unexpected recipients: want atleast 1, got 0"
        cause.getMessage.contains("unexpected recipients")
      case _ => false
    }
}
