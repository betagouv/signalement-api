package services.emails

import models.company.Company
import models.report.Report
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import services.emails.EmailCategory.Consumer
import services.emails.EmailsExamplesUtils._
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

  private def getLocaleOrDefault(locale: Option[Locale]): Locale = locale.getOrElse(Locale.FRENCH)

}
