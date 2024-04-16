package services.emails

import models.report.Report
import models.EmailValidation
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
import services.AttachmentService
import utils.EmailAddress
import utils.FrontRoute

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

trait ConsumerEmail extends Email

object Email {

  // ======= Conso =======

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
