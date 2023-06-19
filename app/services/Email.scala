package services

import cats.data.NonEmptyList
import models.EmailValidation
import models.Subscription
import models.User
import models.auth.AuthToken
import models.company.Company
import models.event.Event
import models.report.Report
import models.report.ReportFile
import models.report.ReportResponse
import models.report.ReportTag
import play.api.libs.mailer.Attachment
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime

sealed trait Email {
  val recipients: Seq[EmailAddress]
  val subject: String
  def getBody: (FrontRoute, EmailAddress) => String
  def getAttachements: AttachmentService => Seq[Attachment] = _.defaultAttachments
}

sealed trait AdminEmail extends Email
sealed trait DgccrfEmail extends Email

sealed trait ProEmail extends Email
sealed trait ProFilteredEmail extends ProEmail {
  val report: Report
}
sealed trait ConsumerEmail extends Email

object Email {

  final case class ResetPassword(user: User, authToken: AuthToken) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(user.email)
    override val subject: String = EmailSubjects.RESET_PASSWORD

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
      views.html.mails.resetPassword(user, authToken)(frontRoute, contactAddress).toString
  }

  final case class ValidateEmail(email: EmailAddress, daysBeforeExpiry: Int, validationUrl: URI) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(email)
    override val subject: String = EmailSubjects.VALIDATE_EMAIL

    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.validateEmail(validationUrl, daysBeforeExpiry).toString
  }

  final case class ProCompanyAccessInvitation(
      recipient: EmailAddress,
      company: Company,
      invitationUrl: URI,
      invitedBy: Option[User]
  ) extends ProEmail {
    override val recipients: List[EmailAddress] = List(recipient)
    override val subject: String = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (_, _) => views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
  }

  final case class ProNewCompanyAccess(
      recipient: EmailAddress,
      company: Company,
      invitedBy: Option[User]
  ) extends ProEmail {
    override val recipients: List[EmailAddress] = List(recipient)
    override val subject: String = EmailSubjects.NEW_COMPANY_ACCESS(company.name)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional
          .newCompanyAccessNotification(frontRoute.dashboard.login, company, invitedBy)(frontRoute)
          .toString
  }

  final case class ProNewReportNotification(userList: NonEmptyList[EmailAddress], report: Report)
      extends ProFilteredEmail {
    override val subject: String = EmailSubjects.NEW_REPORT
    override val recipients: List[EmailAddress] = userList.toList

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportNotification(report)(frontRoute).toString
  }

  final case class ProReportUnreadReminder(
      recipients: List[EmailAddress],
      report: Report,
      reportExpirationDate: OffsetDateTime
  ) extends ProFilteredEmail {
    override val subject: String = EmailSubjects.REPORT_UNREAD_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional.reportUnreadReminder(report, reportExpirationDate)(frontRoute).toString
  }

  final case class ProReportReadReminder(
      recipients: List[EmailAddress],
      report: Report,
      reportExpirationDate: OffsetDateTime
  ) extends ProFilteredEmail {
    override val subject: String = EmailSubjects.REPORT_TRANSMITTED_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional.reportTransmittedReminder(report, reportExpirationDate)(frontRoute).toString
  }

  final case class ProResponseAcknowledgment(report: Report, reportResponse: ReportResponse, user: User)
      extends ProFilteredEmail {
    override val recipients: List[EmailAddress] = List(user.email)
    override val subject: String = EmailSubjects.REPORT_ACK_PRO

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user)(frontRoute).toString
  }

  final case class DgccrfReportNotification(
      recipients: List[EmailAddress],
      subscription: Subscription,
      reports: Seq[(Report, List[ReportFile])],
      startDate: LocalDate
  ) extends DgccrfEmail {
    override val subject: String = EmailSubjects.REPORT_NOTIF_DGCCRF(
      reports.length,
      subscription.withTags.find(_ == ReportTag.ProduitDangereux).map(_ => "[Produits dangereux] ")
    )
    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contact) =>
      views.html.mails.dgccrf.reportNotification(subscription, reports, startDate)(frontRoute, contact).toString
  }

  final case class DgccrfDangerousProductReportNotification(
      recipients: Seq[EmailAddress],
      report: Report
  ) extends DgccrfEmail {
    override val subject: String = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] "))
    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contact) =>
      views.html.mails.dgccrf.reportDangerousProductNotification(report)(frontRoute, contact).toString
  }

  final case class DgccrfAccessLink(recipient: EmailAddress, invitationUrl: URI) extends DgccrfEmail {
    override val subject: String = EmailSubjects.DGCCRF_ACCESS_LINK
    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.dgccrf.accessLink(invitationUrl).toString
    override val recipients: List[EmailAddress] = List(recipient)
  }

  final case class InactiveDgccrfAccount(
      user: User,
      expirationDate: Option[LocalDate]
  ) extends DgccrfEmail {
    override val recipients: Seq[EmailAddress] = List(user.email)
    override val subject: String = EmailSubjects.INACTIVE_DGCCRF_ACCOUNT_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.dgccrf.inactiveAccount(user.fullName, expirationDate)(frontRoute).toString
  }

  final case class AdminAccessLink(recipient: EmailAddress, invitationUrl: URI) extends AdminEmail {
    override val subject: String = EmailSubjects.ADMIN_ACCESS_LINK
    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.admin.accessLink(invitationUrl).toString
    override val recipients: List[EmailAddress] = List(recipient)
  }

  final case class ConsumerReportAcknowledgment(
      report: Report,
      maybeCompany: Option[Company],
      event: Event,
      files: Seq[ReportFile]
  ) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = EmailSubjects.REPORT_ACK

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer.reportAcknowledgment(report, maybeCompany, files.toList)(frontRoute).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.reportAcknowledgmentAttachement(report, maybeCompany, event, files)
  }

  final case class ConsumerProResponseNotification(
      report: Report,
      reportResponse: ReportResponse,
      maybeCompany: Option[Company]
  ) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = EmailSubjects.REPORT_ACK_PRO_CONSUMER

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer
        .reportToConsumerAcknowledgmentPro(
          report,
          maybeCompany,
          reportResponse,
          frontRoute.website.reportReview(report.id.toString)
        )
        .toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.ConsumerProResponseNotificationAttachement
  }

  final case class ConsumerReportClosedNoAction(report: Report, maybeCompany: Option[Company]) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = EmailSubjects.REPORT_CLOSED_NO_ACTION

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer.reportClosedByNoAction(report, maybeCompany)(frontRoute).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.needWorkflowSeqForWorkflowStepN(4, report)

  }

  final case class ConsumerReportClosedNoReading(report: Report, maybeCompany: Option[Company]) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = EmailSubjects.REPORT_CLOSED_NO_READING

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer.reportClosedByNoReading(report, maybeCompany)(frontRoute).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.needWorkflowSeqForWorkflowStepN(3, report)

  }

  final case class ConsumerValidateEmail(emailValidation: EmailValidation) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(emailValidation.email)
    override val subject: String = EmailSubjects.VALIDATE_EMAIL

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
      views.html.mails.consumer
        .confirmEmail(emailValidation.email, emailValidation.confirmationCode)(frontRoute, contactAddress)
        .toString
  }

  final case class ConsumerReportReadByProNotification(report: Report, maybeCompany: Option[Company])
      extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = EmailSubjects.REPORT_TRANSMITTED

    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.consumer.reportTransmission(report, maybeCompany).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.attachmentSeqForWorkflowStepN(3)
  }

}
