package services.emails

import models.company.Company
import models.event.Event
import models.report.Report
import models.report.ReportFile
import models.report.ReportResponse
import models.EmailValidation
import models.User
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
import services.AttachmentService
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.time.Period
import java.util.Locale

trait Email {
  val recipients: Seq[EmailAddress]
  val subject: String
  def getBody: (FrontRoute, EmailAddress) => String
  def getAttachements: AttachmentService => Seq[Attachment] = _.defaultAttachments
}

sealed trait ProEmail         extends Email
sealed trait ProFilteredEmail extends ProEmail
trait ProFilteredEmailSingleReport extends ProFilteredEmail {
  val report: Report
}
trait ProFilteredEmailMultipleReport extends ProFilteredEmail {
  val reports: List[Report]
}

sealed trait ConsumerEmail extends Email

object Email {

  // ======= PRO =======

  final case class ProReportsUnreadReminder(
      recipients: List[EmailAddress],
      reports: List[Report],
      period: Period
  ) extends ProFilteredEmailMultipleReport {
    override val subject: String = EmailSubjects.REPORT_UNREAD_REMINDER

    override def getBody: (FrontRoute, EmailAddress) => String =
      (frontRoute, _) => views.html.mails.professional.reportsUnreadReminder(reports, period)(frontRoute).toString
  }

  final case class ProReportAssignedNotification(report: Report, assigningUser: User, assignedUser: User)
      extends ProFilteredEmailSingleReport {
    override val recipients: List[EmailAddress] = List(assignedUser.email)
    override val subject: String                = EmailSubjects.REPORT_ASSIGNED

    override def getBody: (FrontRoute, EmailAddress) => String = { (frontRoute, _) =>
      views.html.mails.professional.reportAssigned(report, assigningUser, assignedUser)(frontRoute).toString
    }
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

  // ======= Conso =======

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

  private def getLocaleOrDefault(locale: Option[Locale]): Locale = locale.getOrElse(Locale.FRENCH)
}
