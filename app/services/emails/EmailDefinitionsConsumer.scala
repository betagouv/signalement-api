package services.emails

import models.EmailValidation
import models.company.Address
import models.company.Company
import models.event.Event
import models.report._
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
import repositories.subcategorylabel.SubcategoryLabel
import services.AttachmentService
import services.emails.EmailCategory.Consumer
import services.emails.EmailsExamplesUtils._
import utils.Country
import utils.EmailAddress
import utils.FrontRoute

import java.util.Locale

object EmailDefinitionsConsumer {

  case object ConsumerReportDeletionConfirmation extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_deletion_confirmation" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        )
      )

    final case class Email(report: Report, maybeCompany: Option[Company], messagesApi: MessagesApi) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)
      override val recipients: List[EmailAddress]             = List(report.email)
      override val subject: String                            = messagesApi("ConsumerReportDeletionEmail.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.consumer
          .confirmReportDeletionEmail(report, maybeCompany)(frontRoute, messagesProvider)
          .toString
    }
  }

  case object ConsumerReportAcknowledgment extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_ack" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(email = recipient),
            None,
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        ),
        "report_ack_case_reponseconso" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(status = ReportStatus.NA, tags = List(ReportTag.ReponseConso), email = recipient),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_dispute" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(tags = List(ReportTag.LitigeContractuel), email = recipient),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_dangerous_product" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.TraitementEnCours,
                tags = List(ReportTag.ProduitDangereux, ReportTag.BauxPrecaire),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_euro" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Italie)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_euro_and_dispute" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                tags = List(ReportTag.LitigeContractuel),
                companyAddress = Address(country = Some(Country.Islande)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_andorre" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Andorre)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_andorre_and_dispute" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                tags = List(ReportTag.LitigeContractuel),
                companyAddress = Address(country = Some(Country.Andorre)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_suisse" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Suisse)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_suisse_and_dispute" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(
              status = ReportStatus.NA,
              tags = List(ReportTag.LitigeContractuel),
              companyAddress = Address(country = Some(Country.Suisse)),
              email = recipient
            ),
            None,
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        ),
        "report_ack_case_compagnie_aerienne" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                email = recipient,
                tags = List(ReportTag.CompagnieAerienne)
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_abroad_default" ->
          ((recipient, messagesApi) =>
            Email(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Bahamas)),
                email = recipient
              ),
              None,
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_abroad_default_and_dispute" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(
              status = ReportStatus.NA,
              tags = List(ReportTag.LitigeContractuel),
              companyAddress = Address(country = Some(Country.Bahamas)),
              email = recipient
            ),
            None,
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        )
      )

    final case class Email(
        report: Report,
        subcategoryLabel: Option[SubcategoryLabel],
        maybeCompany: Option[Company],
        event: Event,
        files: Seq[ReportFile],
        messagesApi: MessagesApi
    ) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

      override val recipients: List[EmailAddress] = List(report.email)
      override val subject: String                = messagesApi("ReportAckEmail.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.consumer
          .reportAcknowledgment(report, subcategoryLabel, maybeCompany, files.toList)(frontRoute, messagesProvider)
          .toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.reportAcknowledgmentAttachement(report, maybeCompany, event, files, messagesProvider)
    }
  }

  case object ConsumerReportReadByProNotification extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_transmitted" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        )
      )

    final case class Email(
        report: Report,
        maybeCompany: Option[Company],
        messagesApi: MessagesApi
    ) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

      override val recipients: List[EmailAddress] = List(report.email)
      override val subject: String                = messagesApi("ConsumerReportTransmittedEmail.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
        views.html.mails.consumer.reportTransmission(report, maybeCompany).toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.attachmentSeqForWorkflowStepN(3, report.lang.getOrElse(Locale.FRENCH))
    }
  }

  case object ConsumerProResponseNotification extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_ack_pro_consumer" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(email = recipient),
            genReportResponse,
            isReassignable = false,
            Some(genCompany),
            messagesApi
          )
        )
      )

    final case class Email(
        report: Report,
        reportResponse: ExistingReportResponse,
        isReassignable: Boolean,
        maybeCompany: Option[Company],
        messagesApi: MessagesApi
    ) extends BaseEmail {
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
            isReassignable,
            frontRoute.website.reportReview(report.id.toString)
          )(messagesProvider, frontRoute)
          .toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.ConsumerProResponseNotificationAttachement(report.lang.getOrElse(Locale.FRENCH))
    }

  }

  case object ConsumerProEngagementReview extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_pro_engagement_review" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), genReportResponse, isResolved = true, messagesApi)
        )
      )

    final case class Email(
        report: Report,
        maybeCompany: Option[Company],
        reportResponse: ExistingReportResponse,
        isResolved: Boolean,
        messagesApi: MessagesApi
    ) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

      override val recipients: List[EmailAddress] = List(report.email)
      override val subject: String                = messagesApi("ConsumerReportProEngagementEmail.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.consumer
          .reportToConsumerProEngagement(
            report,
            maybeCompany,
            reportResponse,
            isResolved,
            frontRoute.website.engagementReview(report.id.toString)
          )(messagesProvider, frontRoute)
          .toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.ConsumerProResponseNotificationAttachement(report.lang.getOrElse(Locale.FRENCH))
    }
  }

  case object ConsumerProResponseNotificationOnAdminCompletion extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_ack_pro_consumer_on_admin_completion" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        )
      )

    final case class Email(
        report: Report,
        maybeCompany: Option[Company],
        messagesApi: MessagesApi
    ) extends BaseEmail {
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

  }

  case object ConsumerReportClosedNoReading extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_closed_no_reading" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        ),
        "report_closed_no_reading_case_dispute" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
            Some(genCompany),
            messagesApi
          )
        )
      )

    final case class Email(
        report: Report,
        maybeCompany: Option[Company],
        messagesApi: MessagesApi
    ) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

      override val recipients: List[EmailAddress] = List(report.email)
      override val subject: String                = messagesApi("ReportClosedByNoReadingEmail.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.consumer.reportClosedByNoReading(report, maybeCompany)(frontRoute, messagesProvider).toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.needWorkflowSeqForWorkflowStepN(3, report)

    }

  }

  case object ConsumerReportClosedNoAction extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_closed_no_action" -> ((recipient, messagesApi) =>
          Email(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        ),
        "report_closed_no_action_case_dispute" -> ((recipient, messagesApi) =>
          Email(
            genReport.copy(email = recipient, tags = List(ReportTag.LitigeContractuel)),
            Some(genCompany),
            messagesApi
          )
        )
      )

    final case class Email(
        report: Report,
        maybeCompany: Option[Company],
        messagesApi: MessagesApi
    ) extends BaseEmail {
      private val lang                                        = Lang(getLocaleOrDefault(report.lang))
      implicit private val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

      override val recipients: List[EmailAddress] = List(report.email)
      override val subject: String                = messagesApi("ReportNotAnswered.subject")(lang)

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.consumer.reportClosedByNoAction(report, maybeCompany)(frontRoute, messagesProvider).toString

      override def getAttachements: AttachmentService => Seq[Attachment] =
        _.needWorkflowSeqForWorkflowStepN(4, report)

    }

  }
  case object ConsumerValidateEmail extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "validate_email" -> ((recipient, messagesApi) => Email(EmailValidation(email = recipient), None, messagesApi))
      )

    final case class Email(
        emailValidation: EmailValidation,
        locale: Option[Locale],
        messagesApi: MessagesApi
    ) extends BaseEmail {
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

  }

  private def getLocaleOrDefault(locale: Option[Locale]): Locale = locale.getOrElse(Locale.FRENCH)

}
