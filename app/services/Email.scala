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
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute
import utils.SIREN

import java.net.URI
import java.time.LocalDate
import java.time.Period
import java.util.Locale

sealed trait Email {
  val recipients: Seq[EmailAddress]
  val subject: String
  def getBody: (FrontRoute, EmailAddress) => String
  def getAttachements: AttachmentService => Seq[Attachment] = _.defaultAttachments
}

sealed trait AdminEmail  extends Email
sealed trait DgccrfEmail extends Email

sealed trait ProEmail         extends Email
sealed trait ProFilteredEmail extends ProEmail
sealed trait ProFilteredEmailSingleReport extends ProFilteredEmail {
  val report: Report
}
sealed trait ProFilteredEmailMultipleReport extends ProFilteredEmail {
  val reports: List[Report]
}
sealed trait ConsumerEmail extends Email

object Email {

  final case class ResetPassword(user: User, authToken: AuthToken) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(user.email)
    override val subject: String                = EmailSubjects.RESET_PASSWORD

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
      views.html.mails.resetPassword(user, authToken)(frontRoute, contactAddress).toString
  }

  final case class ValidateEmail(email: EmailAddress, daysBeforeExpiry: Int, validationUrl: URI) extends ConsumerEmail {
    override val recipients: List[EmailAddress] = List(email)
    override val subject: String                = EmailSubjects.VALIDATE_EMAIL

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
    override val subject: String                = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (_, _) => views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
  }

  final case class ProCompaniesAccessesInvitations(
      recipient: EmailAddress,
      companies: List[Company],
      siren: SIREN,
      invitationUrl: URI
  ) extends ProEmail {
    override val recipients: List[EmailAddress] = List(recipient)
    override val subject: String                = EmailSubjects.PRO_COMPANIES_ACCESSES_INVITATIONS(siren)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (_, _) =>
        views.html.mails.professional.companiesAccessesInvitations(invitationUrl, companies, siren.value).toString
  }

  final case class ProNewCompanyAccess(
      recipient: EmailAddress,
      company: Company,
      invitedBy: Option[User]
  ) extends ProEmail {
    override val recipients: List[EmailAddress] = List(recipient)
    override val subject: String                = EmailSubjects.NEW_COMPANY_ACCESS(company.name)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional
          .newCompanyAccessNotification(frontRoute.dashboard.login, company, invitedBy)(frontRoute)
          .toString
  }

  final case class ProNewCompaniesAccesses(
      recipient: EmailAddress,
      companies: List[Company],
      siren: SIREN
  ) extends ProEmail {
    override val recipients: List[EmailAddress] = List(recipient)
    override val subject: String                = EmailSubjects.PRO_NEW_COMPANIES_ACCESSES(siren)

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional
          .newCompaniesAccessesNotification(frontRoute.dashboard.login, companies, siren.value)(frontRoute)
          .toString
  }

  final case class ProNewReportNotification(userList: NonEmptyList[EmailAddress], report: Report)
      extends ProFilteredEmailSingleReport {
    override val subject: String                = EmailSubjects.NEW_REPORT
    override val recipients: List[EmailAddress] = userList.toList

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportNotification(report)(frontRoute).toString
  }

  final case class ProReportsUnreadReminder(
      recipients: List[EmailAddress],
      reports: List[Report],
      period: Period
  ) extends ProFilteredEmailMultipleReport {
    override val subject: String = EmailSubjects.REPORT_UNREAD_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportsUnreadReminder(reports, period)(frontRoute).toString
  }

  final case class ProReportsReadReminder(
      recipients: List[EmailAddress],
      reports: List[Report],
      period: Period
  ) extends ProFilteredEmailMultipleReport {
    override val subject: String = EmailSubjects.REPORT_TRANSMITTED_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportsTransmittedReminder(reports, period)(frontRoute).toString
  }

  final case class ProResponseAcknowledgment(report: Report, reportResponse: ReportResponse, user: User)
      extends ProFilteredEmailSingleReport {
    override val recipients: List[EmailAddress] = List(user.email)
    override val subject: String                = EmailSubjects.REPORT_ACK_PRO

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) =>
        views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user)(frontRoute).toString
  }

  final case class ProResponseAcknowledgmentOnAdminCompletion(report: Report, users: List[User])
      extends ProFilteredEmailSingleReport {
    override val recipients: List[EmailAddress] = users.map(_.email)
    override val subject: String                = EmailSubjects.REPORT_ACK_PRO_ON_ADMIN_COMPLETION

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportAcknowledgmentProOnAdminCompletion(frontRoute).toString
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

  final case class AgentAccessLink(role: String)(recipient: EmailAddress, invitationUrl: URI) extends DgccrfEmail {
    override val subject: String = EmailSubjects.DGCCRF_ACCESS_LINK
    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.dgccrf.accessLink(invitationUrl, role).toString
    override val recipients: List[EmailAddress] = List(recipient)
  }

  final case class InactiveDgccrfAccount(
      user: User,
      expirationDate: Option[LocalDate]
  ) extends DgccrfEmail {
    override val recipients: Seq[EmailAddress] = List(user.email)
    override val subject: String               = EmailSubjects.INACTIVE_DGCCRF_ACCOUNT_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.dgccrf.inactiveAccount(user.fullName, expirationDate)(frontRoute).toString
  }

  final case class AdminAccessLink(recipient: EmailAddress, invitationUrl: URI) extends AdminEmail {
    override val subject: String = EmailSubjects.ADMIN_ACCESS_LINK
    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.admin.accessLink(invitationUrl).toString
    override val recipients: List[EmailAddress] = List(recipient)
  }

  final case class ReportDeletionConfirmation(report: Report, maybeCompany: Option[Company], messagesApi: MessagesApi)
      extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)
    override val recipients: List[EmailAddress]             = List(report.email)
    override val subject: String                            = messagesApi("ConsumerReportDeletionEmail.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer
        .confirmReportDeletionEmail(report, maybeCompany)(frontRoute, messagesProvider)
        .toString
  }

  final case class ConsumerReportAcknowledgment(
      report: Report,
      maybeCompany: Option[Company],
      event: Event,
      files: Seq[ReportFile],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String                = messagesApi("ReportAckEmail.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer
        .reportAcknowledgment(report, maybeCompany, files.toList)(frontRoute, messagesProvider)
        .toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.reportAcknowledgmentAttachement(report, maybeCompany, event, files, messagesProvider)
  }

  final case class ConsumerProResponseNotification(
      report: Report,
      reportResponse: ReportResponse,
      maybeCompany: Option[Company],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String                = messagesApi("ConsumerReportAckProEmail.subject")(lang)

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
      _.ConsumerProResponseNotificationAttachement(report.lang.getOrElse(Locale.FRENCH))
  }

  final case class ConsumerProResponseNotificationOnAdminCompletion(
      report: Report,
      maybeCompany: Option[Company],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String = messagesApi("ConsumerReportAckProEmailOnAdminCompletion.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.consumer
        .reportToConsumerAcknowledgmentOnAdminCompletion(
          report,
          maybeCompany
        )
        .toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      s => s.defaultAttachments ++ s.attachmentSeqForWorkflowStepN(4, report.lang.getOrElse(Locale.FRENCH))
  }

  final case class ConsumerReportClosedNoAction(report: Report, maybeCompany: Option[Company], messagesApi: MessagesApi)
      extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String                = messagesApi("ReportNotAnswered.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer.reportClosedByNoAction(report, maybeCompany)(frontRoute, messagesProvider).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.needWorkflowSeqForWorkflowStepN(4, report)

  }

  final case class ConsumerReportClosedNoReading(
      report: Report,
      maybeCompany: Option[Company],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String                = messagesApi("ReportClosedByNoReadingEmail.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
      views.html.mails.consumer.reportClosedByNoReading(report, maybeCompany)(frontRoute, messagesProvider).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.needWorkflowSeqForWorkflowStepN(3, report)

  }

  final case class ConsumerValidateEmail(
      emailValidation: EmailValidation,
      locale: Option[Locale],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(locale))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(emailValidation.email)
    override val subject: String                = messagesApi("ConsumerValidateEmail.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
      views.html.mails.consumer
        .confirmEmail(emailValidation.email, emailValidation.confirmationCode)(
          frontRoute,
          contactAddress,
          messagesProvider
        )
        .toString
  }

  final case class ConsumerReportReadByProNotification(
      report: Report,
      maybeCompany: Option[Company],
      messagesApi: MessagesApi
  ) extends ConsumerEmail {
    private val lang                                        = Lang(getLocaleOrDefault(report.lang))
    implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    override val recipients: List[EmailAddress] = List(report.email)
    override val subject: String                = messagesApi("ConsumerReportTransmittedEmail.subject")(lang)

    override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
      views.html.mails.consumer.reportTransmission(report, maybeCompany).toString

    override def getAttachements: AttachmentService => Seq[Attachment] =
      _.attachmentSeqForWorkflowStepN(3, report.lang.getOrElse(Locale.FRENCH))
  }

  private def getLocaleOrDefault(locale: Option[Locale]): Locale = locale.getOrElse(Locale.FRENCH)
}
