package services

import actors.EmailActor.EmailRequest
import akka.actor.ActorRef
import akka.pattern.ask
import models._
import play.api.Configuration
import play.api.Logger
import play.api.libs.mailer.Attachment
import play.api.libs.mailer.AttachmentData
import play.api.mvc.Request
import repositories.ReportNotificationBlockedRepository
import utils.Constants.Tags
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MailService @Inject() (
    @Named("email-actor") actor: ActorRef,
    configuration: Configuration,
    mailerService: MailerService,
    reportNotificationBlocklistRepo: ReportNotificationBlockedRepository,
    implicit val frontRoute: FrontRoute,
    val pdfService: PDFService
)(implicit
    private[this] val executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)
  private[this] val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  implicit private[this] val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")
  implicit private[this] val timeout: akka.util.Timeout = 5.seconds

  def send(
      from: EmailAddress,
      recipients: Seq[EmailAddress],
      subject: String,
      bodyHtml: String,
      blindRecipients: Seq[EmailAddress] = Seq.empty,
      attachments: Seq[Attachment] = Seq.empty,
      times: Int = 0
  ): Unit =
    if (recipients.exists(_.value != "")) {
      actor ? EmailRequest(
        from = from,
        recipients = recipients,
        subject = subject,
        bodyHtml = bodyHtml,
        blindRecipients = blindRecipients,
        attachments = attachments,
        times = times
      )
    }

  private[this] def sendIfAuthorized(adminMails: List[EmailAddress], report: Report)(cb: (List[EmailAddress]) => Unit) =
    report.companyId.map { companyId =>
      reportNotificationBlocklistRepo
        .filterBlockedEmails(adminMails, companyId)
        .filter(_.nonEmpty)
        .map(cb)
    }

  object Common {

    def sendResetPassword(user: User, authToken: AuthToken): Unit = {
      send(
        from = mailFrom,
        recipients = Seq(user.email),
        subject = EmailSubjects.RESET_PASSWORD,
        bodyHtml = views.html.mails.resetPassword(user, authToken).toString
      )
      logger.debug(s"Sent password reset to ${user.email}")
    }

    def sendValidateEmail(user: User, validationUrl: URI): Unit =
      send(
        from = mailFrom,
        recipients = Seq(user.email),
        subject = EmailSubjects.VALIDATE_EMAIL,
        bodyHtml = views.html.mails.validateEmail(validationUrl).toString
      )
  }

  object Consumer {

    def sendEmailConfirmation(email: EmailValidation)(implicit request: Request[Any]) =
      send(
        from = mailFrom,
        recipients = Seq(email.email),
        subject = EmailSubjects.VALIDATE_EMAIL,
        bodyHtml = views.html.mails.consumer.confirmEmail(email.email, email.confirmationCode).toString
      )

    def sendReportClosedByNoReading(report: Report): Unit =
      send(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = EmailSubjects.REPORT_CLOSED_NO_READING,
        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(3).filter(_ => report.needWorkflowAttachment())
      )

    def sendAttachmentSeqForWorkflowStepN(report: Report): Unit =
      send(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = EmailSubjects.REPORT_CLOSED_NO_ACTION,
        bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(4).filter(_ => report.needWorkflowAttachment())
      )

    def sendReportToConsumerAcknowledgmentPro(report: Report, reportResponse: ReportResponse): Unit =
      send(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = EmailSubjects.REPORT_ACK_PRO_CONSUMER,
        bodyHtml = views.html.mails.consumer
          .reportToConsumerAcknowledgmentPro(
            report,
            reportResponse,
            frontRoute.dashboard.reportReview(report.id.toString)
          )
          .toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(4)
      )

    def sendReportTransmission(report: Report): Unit =
      send(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = EmailSubjects.REPORT_TRANSMITTED,
        bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(3)
      )

    def sendReportAcknowledgment(report: Report, event: Event, files: Seq[ReportFile]): Unit =
      send(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = EmailSubjects.REPORT_ACK,
        bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files.toList).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(2).filter(_ => report.needWorkflowAttachment()) ++
          Seq(
            AttachmentData(
              "Signalement.pdf",
              pdfService.getPdfData(views.html.pdfs.report(report, Seq((event, None)), None, Seq.empty, files)),
              "application/pdf"
            )
          ).filter(_ => report.isContractualDispute() && report.companyId.isDefined)
      )
  }

  object Pro {

    def sendReportUnreadReminder(adminMails: List[EmailAddress], report: Report, expirationDate: OffsetDateTime): Unit =
      sendIfAuthorized(adminMails, report)(filteredAdminEmails =>
        send(
          from = mailFrom,
          recipients = filteredAdminEmails,
          subject = EmailSubjects.REPORT_UNREAD_REMINDER,
          bodyHtml = views.html.mails.professional.reportUnreadReminder(report, expirationDate).toString
        )
      )

    def sendReportTransmittedReminder(
        adminMails: List[EmailAddress],
        report: Report,
        expirationDate: OffsetDateTime
    ): Unit =
      sendIfAuthorized(adminMails, report)(filteredAdminEmails =>
        send(
          from = mailFrom,
          recipients = filteredAdminEmails,
          subject = EmailSubjects.REPORT_TRANSMITTED_REMINDER,
          bodyHtml = views.html.mails.professional.reportTransmittedReminder(report, expirationDate).toString
        )
      )

    def sendReportAcknowledgmentPro(user: User, report: Report, reportResponse: ReportResponse): Unit =
      sendIfAuthorized(List(user.email), report)(filteredAdminEmails =>
        send(
          from = mailFrom,
          recipients = filteredAdminEmails,
          subject = EmailSubjects.REPORT_ACK_PRO,
          bodyHtml = views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user).toString
        )
      )

    def sendCompanyAccessInvitation(
        company: Company,
        email: EmailAddress,
        invitationUrl: URI,
        invitedBy: Option[User]
    ): Unit =
      send(
        from = mailFrom,
        recipients = Seq(email),
        subject = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name),
        bodyHtml = views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
      )

    def sendReportNotification(admins: List[EmailAddress], report: Report): Unit =
      sendIfAuthorized(admins, report)(filteredAdminEmails =>
        send(
          from = mailFrom,
          recipients = filteredAdminEmails,
          subject = EmailSubjects.NEW_REPORT,
          bodyHtml = views.html.mails.professional.reportNotification(report).toString
        )
      )

    def sendNewCompanyAccessNotification(user: User, company: Company, invitedBy: Option[User]): Unit =
      send(
        from = mailFrom,
        recipients = Seq(user.email),
        subject = EmailSubjects.NEW_COMPANY_ACCESS(company.name),
        bodyHtml = views.html.mails.professional
          .newCompanyAccessNotification(frontRoute.dashboard.login, company, invitedBy)
          .toString
      )
  }

  object Dgccrf {

    def sendDangerousProductEmail(emails: Seq[EmailAddress], report: Report) =
      send(
        from = mailFrom,
        recipients = emails,
        subject = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] ")),
        bodyHtml = views.html.mails.dgccrf.reportDangerousProductNotification(report).toString
      )

    def sendAccessLink(email: EmailAddress, invitationUrl: URI): Unit =
      send(
        from = mailFrom,
        recipients = Seq(email),
        subject = EmailSubjects.DGCCRF_ACCESS_LINK,
        bodyHtml = views.html.mails.dgccrf.accessLink(invitationUrl).toString
      )

    def sendMailReportNotification(
        email: EmailAddress,
        subscription: Subscription,
        reports: List[Report],
        startDate: LocalDate
    ) =
      if (reports.nonEmpty) {
        logger.debug(
          s"sendMailReportNotification $email - abonnement ${subscription.id} - ${reports.length} signalements"
        )
        send(
          from = mailFrom,
          recipients = Seq(email),
          subject = EmailSubjects.REPORT_NOTIF_DGCCRF(
            reports.length,
            subscription.tags.find(_ == Tags.DangerousProduct).map(_ => "[Produits dangereux] ")
          ),
          bodyHtml = views.html.mails.dgccrf.reportNotification(subscription, reports, startDate).toString
        )
      }
  }
}
