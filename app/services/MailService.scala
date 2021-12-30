package services

import actors.EmailActor.EmailRequest
import akka.actor.ActorRef
import akka.pattern.ask
import config.AppConfigLoader
import play.api.Logger
import play.api.libs.mailer.Attachment
import repositories.ReportNotificationBlockedRepository
import utils.EmailAddress
import utils.FrontRoute

import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class MailService @Inject() (
    @Named("email-actor") actor: ActorRef,
    appConfigLoader: AppConfigLoader,
    reportNotificationBlocklistRepo: ReportNotificationBlockedRepository,
    implicit val frontRoute: FrontRoute,
    val pdfService: PDFService,
    attachementService: AttachementService
)(implicit
    private[this] val executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)
  private[this] val mailFrom = appConfigLoader.get.mail.from
  implicit private[this] val contactAddress = appConfigLoader.get.mail.contactAddress
  implicit private[this] val timeout: akka.util.Timeout = 5.seconds

  def send(
      email: Email
  ): Future[Unit] = email match {
    case email: ProFilteredEmail => filterBlockedAndSend(email)
    case email =>
      send(
        email.recipients,
        email.subject,
        email.getBody(frontRoute, contactAddress),
        email.getAttachements(attachementService)
      )
  }

  /** Filter pro user recipients that are excluded from notifications and send email
    */
  private def filterBlockedAndSend(email: ProFilteredEmail): Future[Unit] =
    email.report.companyId match {
      case Some(companyId) =>
        reportNotificationBlocklistRepo
          .filterBlockedEmails(email.recipients, companyId)
          .flatMap {
            case Nil =>
              logger.debug("All emails filtered, ignoring email delivery")
              Future.successful(())
            case filteredRecipients =>
              send(
                filteredRecipients,
                email.subject,
                email.getBody(frontRoute, contactAddress),
                email.getAttachements(attachementService)
              )
          }
      case None =>
        logger.debug("No company linked to report, not sending emails")
        Future.successful(())
    }

  private def send(
      recipients: Seq[EmailAddress],
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment]
  ): Future[Unit] =
    if (recipients.exists(_.nonEmpty)) {
      val emailRequest = EmailRequest(
        from = mailFrom,
        recipients = recipients.filter(_.nonEmpty),
        subject = subject,
        bodyHtml = bodyHtml,
        attachments = attachments
      )

      (actor ? emailRequest).map(_ => ())
    } else Future.successful(())

//  /** Filter pro user recipients that are excluded from notifications and send email
//    */
//  def filterBlockedAndSend(
//      recipients: List[EmailAddress],
//      subject: String,
//      bodyHtml: String,
//      report: Report,
//      blindRecipients: Seq[EmailAddress] = Seq.empty,
//      attachments: Seq[Attachment] = Seq.empty,
//      times: Int = 0
//  ): Future[Unit] =
//    report.companyId match {
//      case Some(companyId) =>
//        reportNotificationBlocklistRepo
//          .filterBlockedEmails(recipients, companyId)
//          .flatMap {
//            case Nil =>
//              logger.debug("All emails filtered, ignoring email delivery")
//              Future.successful(())
//            case filteredRecipients =>
//              send(filteredRecipients, subject, bodyHtml, blindRecipients, attachments, times)
//          }
//      case None =>
//        logger.debug("No company linked to report, not sending emails")
//        Future.successful(())
//    }

//  def attachmentSeqForWorkflowStepN(n: Int) = Seq(
//    AttachmentFile(
//      s"schemaSignalConso-Etape$n.png",
//      environment.getFile(s"/appfiles/schemaSignalConso-Etape$n.png"),
//      contentId = Some(s"schemaSignalConso-Etape$n")
//    )
//  )

//  private[this] def sendIfAuthorized(adminMails: List[EmailAddress], report: Report)(
//      cb: (List[EmailAddress]) => Unit
//  ): Future[Unit] =
//    report.companyId match {
//      case Some(companyId) =>
//        reportNotificationBlocklistRepo
//          .filterBlockedEmails(adminMails, companyId)
//          .filter(_.nonEmpty)
//          .map(x => cb(x))
//
//      case None =>
//        logger.debug("No company linked to report, not sending emails")
//        Future.successful(())
//    }

//  object Common {

//    def sendResetPassword(user: User, authToken: AuthToken): Unit = {
//      send(
//        recipients = Seq(user.email),
//        subject = EmailSubjects.RESET_PASSWORD,
//        bodyHtml = views.html.mails.resetPassword(user, authToken).toString
//      )
//      logger.debug(s"Sent password reset to ${user.email}")
//    }

//    def sendValidateEmail(user: User, validationUrl: URI): Unit =
//      send(
//        recipients = Seq(user.email),
//        subject = EmailSubjects.VALIDATE_EMAIL,
//        bodyHtml = views.html.mails.validateEmail(validationUrl).toString
//      )
//  }

//  object Consumer {

//    def sendEmailConfirmation(email: EmailValidation)(implicit request: Request[Any]) =
//      send(
//        recipients = Seq(email.email),
//        subject = EmailSubjects.VALIDATE_EMAIL,
//        bodyHtml = views.html.mails.consumer.confirmEmail(email.email, email.confirmationCode).toString
//      )

//    def sendReportClosedByNoReading(report: Report): Unit =
//      send(
//        recipients = Seq(report.email),
//        subject = EmailSubjects.REPORT_CLOSED_NO_READING,
//        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
//        attachments = attachmentSeqForWorkflowStepN(3).filter(_ => report.needWorkflowAttachment())
//      )

//    def sendAttachmentSeqForWorkflowStepN(report: Report): Unit =
//      send(
//        recipients = Seq(report.email),
//        subject = EmailSubjects.REPORT_CLOSED_NO_ACTION,
//        bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
//        attachments = attachmentSeqForWorkflowStepN(4).filter(_ => report.needWorkflowAttachment())
//      )

//    def sendReportToConsumerAcknowledgmentPro(report: Report, reportResponse: ReportResponse): Future[Unit] =
//      send(
//        recipients = Seq(report.email),
//        subject = EmailSubjects.REPORT_ACK_PRO_CONSUMER,
//        bodyHtml = views.html.mails.consumer
//          .reportToConsumerAcknowledgmentPro(
//            report,
//            reportResponse,
//            frontRoute.dashboard.reportReview(report.id.toString)
//          )
//          .toString,
//        attachments = attachmentSeqForWorkflowStepN(4)
//      )

//    def sendReportTransmission(report: Report): Unit =
//      send(
//        recipients = Seq(report.email),
//        subject = EmailSubjects.REPORT_TRANSMITTED,
//        bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
//        attachments = attachmentSeqForWorkflowStepN(3)
//      )

//    def sendReportAcknowledgment(report: Report, event: Event, files: Seq[ReportFile]): Unit =
//      send(
//        recipients = Seq(report.email),
//        subject = EmailSubjects.REPORT_ACK,
//        bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files.toList).toString,
//        attachments = attachmentSeqForWorkflowStepN(2).filter(_ => report.needWorkflowAttachment()) ++
//          Seq(
//            AttachmentData(
//              "Signalement.pdf",
//              pdfService.getPdfData(views.html.pdfs.report(report, Seq((event, None)), None, Seq.empty, files)),
//              "application/pdf"
//            )
//          ).filter(_ => report.isContractualDispute() && report.companyId.isDefined)
//      )
//  }

//  object Pro {
//
////    def sendCompanyAccessInvitation(
////        company: Company,
////        email: EmailAddress,
////        invitationUrl: URI,
////        invitedBy: Option[User]
////    ): Unit =
////      send(
////        recipients = Seq(email),
////        subject = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name),
////        bodyHtml = views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
////      )
//
////    def sendReportNotification(admins: List[EmailAddress], report: Report): Unit =
////      sendIfAuthorized(admins, report) { filteredAdminEmails =>
////        send(
////          recipients = filteredAdminEmails,
////          subject = EmailSubjects.NEW_REPORT,
////          bodyHtml = views.html.mails.professional.reportNotification(report).toString
////        )
////      }
//
////    def sendNewCompanyAccessNotification(user: User, company: Company, invitedBy: Option[User]): Unit =
////      send(
////        recipients = Seq(user.email),
////        subject = EmailSubjects.NEW_COMPANY_ACCESS(company.name),
////        bodyHtml = views.html.mails.professional
////          .newCompanyAccessNotification(frontRoute.dashboard.login, company, invitedBy)
////          .toString
////      )
//  }

//  object Dgccrf {
//
////    def sendDangerousProductEmail(emails: Seq[EmailAddress], report: Report) =
////      send(
////        recipients = emails,
////        subject = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] ")),
////        bodyHtml = views.html.mails.dgccrf.reportDangerousProductNotification(report).toString
////      )
//
////    def sendAccessLink(email: EmailAddress, invitationUrl: URI): Unit =
////      send(
////        recipients = Seq(email),
////        subject = EmailSubjects.DGCCRF_ACCESS_LINK,
////        bodyHtml = views.html.mails.dgccrf.accessLink(invitationUrl).toString
////      )
//
////    def sendMailReportNotification(
////        email: EmailAddress,
////        subscription: Subscription,
////        reports: List[Report],
////        startDate: LocalDate
////    ) =
////      if (reports.nonEmpty) {
////        logger.debug(
////          s"sendMailReportNotification $email - abonnement ${subscription.id} - ${reports.length} signalements"
////        )
////        send(
////          recipients = Seq(email),
////          subject = EmailSubjects.REPORT_NOTIF_DGCCRF(
////            reports.length,
////            subscription.tags.find(_ == Tags.DangerousProduct).map(_ => "[Produits dangereux] ")
////          ),
////          bodyHtml = views.html.mails.dgccrf.reportNotification(subscription, reports, startDate).toString
////        )
////      }
//  }
}
