package services.emails

import models.company.Address
import models.company.Company
import models.event.Event
import models.report.Report
import models.report.ReportFile
import models.report.ReportResponse
import models.report.ReportStatus
import models.report.ReportTag
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
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
          EmailImpl(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        )
      )

    final case class EmailImpl(report: Report, maybeCompany: Option[Company], messagesApi: MessagesApi) extends Email {
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
          EmailImpl(
            genReport.copy(email = recipient),
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        ),
        "report_ack_case_reponseconso" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(status = ReportStatus.NA, tags = List(ReportTag.ReponseConso), email = recipient),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_dispute" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(tags = List(ReportTag.LitigeContractuel), email = recipient),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_dangerous_product" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.TraitementEnCours,
                tags = List(ReportTag.ProduitDangereux),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_euro" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Italie)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_euro_and_dispute" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                tags = List(ReportTag.LitigeContractuel),
                companyAddress = Address(country = Some(Country.Islande)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_andorre" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Andorre)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_andorre_and_dispute" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                tags = List(ReportTag.LitigeContractuel),
                companyAddress = Address(country = Some(Country.Andorre)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_suisse" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Suisse)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_suisse_and_dispute" -> ((recipient, messagesApi) =>
          EmailImpl(
            genReport.copy(
              status = ReportStatus.NA,
              tags = List(ReportTag.LitigeContractuel),
              companyAddress = Address(country = Some(Country.Suisse)),
              email = recipient
            ),
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        ),
        "report_ack_case_compagnie_aerienne" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                email = recipient,
                tags = List(ReportTag.CompagnieAerienne)
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_abroad_default" ->
          ((recipient, messagesApi) =>
            EmailImpl(
              genReport.copy(
                status = ReportStatus.NA,
                companyAddress = Address(country = Some(Country.Bahamas)),
                email = recipient
              ),
              Some(genCompany),
              genEvent,
              Nil,
              messagesApi
            )
          ),
        "report_ack_case_abroad_default_and_dispute" -> ((recipient, messagesApi) =>
          EmailImpl(
            genReport.copy(
              status = ReportStatus.NA,
              tags = List(ReportTag.LitigeContractuel),
              companyAddress = Address(country = Some(Country.Bahamas)),
              email = recipient
            ),
            Some(genCompany),
            genEvent,
            Nil,
            messagesApi
          )
        )
      )

    final case class EmailImpl(
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
  }

  case object ConsumerReportReadByProNotification extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_transmitted" -> ((recipient, messagesApi) =>
          EmailImpl(genReport.copy(email = recipient), Some(genCompany), messagesApi)
        )
      )

    final case class EmailImpl(
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
  }

  case object ConsumerProResponseNotification extends EmailDefinition {
    override val category = Consumer

    override def examples =
      Seq(
        "report_ack_pro_consumer" -> ((recipient, messagesApi) =>
          EmailImpl(genReport.copy(email = recipient), genReportResponse, Some(genCompany), messagesApi)
        )
      )

    final case class EmailImpl(
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

  }

  private def getLocaleOrDefault(locale: Option[Locale]): Locale = locale.getOrElse(Locale.FRENCH)

}
