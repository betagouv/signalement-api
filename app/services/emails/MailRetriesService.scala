package services.emails

import org.apache.pekko.actor.ActorSystem
import cats.data.NonEmptyList
import com.sun.mail.smtp.SMTPSendFailedException
import play.api.Logger
import play.api.libs.mailer._
import services.emails.MailRetriesService.EmailRequest
import services.emails.MailRetriesService.getDelayBeforeNextRetry
import utils.EmailAddress
import utils.Logs.RichLogger

import java.util.concurrent.Executors
import javax.mail.internet.AddressException
import scala.concurrent.duration.DurationDouble
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

object MailRetriesService {

  case class EmailRequest(
      from: EmailAddress,
      recipients: NonEmptyList[EmailAddress],
      subject: String,
      bodyHtml: String,
      blindRecipients: Seq[EmailAddress] = Seq.empty,
      attachments: Seq[Attachment] = Seq.empty
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

class MailRetriesService(mailerClient: MailerClient, executionContext: ExecutionContext, actorSystem: ActorSystem) {

  val logger: Logger = Logger(this.getClass)

  // Dedicated thread pool
  val executionContextForBlockingSmtpCalls = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private def sendEmail(
      req: EmailRequest
  ): Unit =
    // synchronous, and can block for quite a while
    mailerClient.send(
      Email(
        subject = req.subject,
        from = req.from.value,
        to = req.recipients.map(_.value).toList,
        bcc = req.blindRecipients.map(_.value),
        bodyHtml = Some(req.bodyHtml),
        attachments = req.attachments
      )
    ): Unit

  def sendEmailWithRetries(emailRequest: EmailRequest): Unit =
    sendEmailWithRetries(emailRequest, numAttempt = 1)

  private def sendEmailWithRetries(emailRequest: EmailRequest, numAttempt: Int): Unit = {
    val logDetails =
      s"""(attempt #$numAttempt, to ${emailRequest.recipients.toList.mkString(
          ", "
        )}, subject "${emailRequest.subject}")"""

    val future = Future {
      logger.info("@@@@@@@@@@")
      logger.info("@@@@@@@@@@@@@@")
      logger.info("@@@@@@@@@@@@@@@@@@")
      logger.infoWithTitle("email_sending_attempt", s"Sending email $logDetails")
      logger.info("@@@@@@@@@@@@@@@@@@")
      logger.info("@@@@@@@@@@@@@@")
      logger.info("@@@@@@@@@@")
      // /!\ synchronous, blocks the thread
      sendEmail(emailRequest)
    }(executionContextForBlockingSmtpCalls)

    future.onComplete {
      case Success(_) =>
        logger.infoWithTitle("email_sent", s"Sent email $logDetails")
      case Failure(e) if isCausedByAddressException(e) =>
        logger.warnWithTitle(
          "email_malformed_address",
          s"Malformed email address $logDetails"
        )
      case Failure(e) if isCausedByEmptyDomainName(e) =>
        logger.warnWithTitle(
          "email_malformed_address",
          s"Malformed email address $logDetails"
        )
      case Failure(e) if isCausedByUnexceptedRecipients(e) =>
        logger.warnWithTitle(
          "email_unexpected_recipients",
          s"Received unexpected recipients error $logDetails"
        )
      case Failure(e) =>
        logger.errorWithTitle(
          "email_sending_failed",
          s"Unexpected error when sending email $logDetails",
          e
        )
        getDelayBeforeNextRetry(numAttempt, withRandomJitter = true) match {
          case Some(delay) =>
            actorSystem.scheduler.scheduleOnce(delay) {
              sendEmailWithRetries(emailRequest, numAttempt = numAttempt + 1)
            }(executionContext) // I think here it's safe to use the main execution context
          case None =>
            logger.errorWithTitle(
              "email_max_delivery_attempts",
              s"Email has reached max delivery attempts, aborting delivery $logDetails"
            )
        }
    }(executionContext) // Same here, I think we can just use the main execution context
    ()
  }

  private def isCausedByAddressException(e: Throwable): Boolean =
    e.getCause match {
      case null                => false
      case _: AddressException => true
      case _                   => false
    }

  // This case happens when trying to send through Sendinblue to the email "......@gmail.com"
  // (with the dots exactly like that)
  // It seems we don't get an AddressException, but it's Sendinblue that answers in a weird way
  private def isCausedByUnexceptedRecipients(e: Throwable): Boolean =
    e.getCause match {
      case null                           => false
      case cause: SMTPSendFailedException =>
        // The full message with "......@gmail.com" was "400 unexpected recipients: want atleast 1, got 0"
        cause.getMessage.contains("unexpected recipients")
      case _ => false
    }

  private def isCausedByEmptyDomainName(e: Throwable): Boolean =
    e match {
      case _: IllegalArgumentException => e.getMessage.contains("Empty label is not a legal name")
      case _                           => false
    }
}
