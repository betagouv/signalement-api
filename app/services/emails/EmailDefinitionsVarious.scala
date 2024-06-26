package services.emails

import models.User
import models.auth.AuthToken
import services.emails.EmailCategory.Various
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI
object EmailDefinitionsVarious {

  case object ResetPassword extends EmailDefinition {
    override val category = Various

    final case class Email(user: User, authToken: AuthToken) extends BaseEmail {
      override val recipients: List[EmailAddress] = List(user.email)
      override val subject: String                = EmailSubjects.RESET_PASSWORD
      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
        views.html.mails.resetPassword(user, authToken)(frontRoute, contactAddress).toString
    }

    override def examples =
      Seq("reset_password" -> ((recipient, _) => Email(genUser.copy(email = recipient), genAuthToken)))

  }

  case object UpdateEmailAddress extends EmailDefinition {
    override val category = Various

    final case class Email(recipient: EmailAddress, invitationUrl: URI, daysBeforeExpiry: Int) extends BaseEmail {

      override val recipients: Seq[EmailAddress] = List(recipient)
      override val subject: String               = EmailSubjects.UPDATE_EMAIL_ADDRESS

      override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
        views.html.mails.updateEmailAddress(invitationUrl, daysBeforeExpiry).toString()
    }
    override def examples =
      Seq("update_email_address" -> ((recipient, _) => Email(recipient, dummyURL, daysBeforeExpiry = 2)))

  }
}
