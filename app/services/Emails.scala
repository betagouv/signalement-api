package services

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.User
import models.auth.AuthToken
import services.EmailCategory.Various
import services.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI

sealed trait EmailCategory extends EnumEntry

object EmailCategory extends PlayEnum[EmailCategory] {
  override def values: IndexedSeq[EmailCategory] = findValues

  case object Various extends EmailCategory

  case object Admin extends EmailCategory

  case object Dgccrf extends EmailCategory
  case object Pro    extends EmailCategory
  case object Conso  extends EmailCategory

}

trait EmailDefinition {
  val category: EmailCategory
  def examples: Seq[(String, EmailAddress => Email)]

}

object EmailDefinitions {

  case object ResetPassword extends EmailDefinition {
    override val category = Various
    override def examples =
      Seq("reset_password" -> (recipient => build(genUser.copy(email = recipient), genAuthToken)))

    def build(user: User, authToken: AuthToken): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(user.email)
        override val subject: String                = EmailSubjects.RESET_PASSWORD
        override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contactAddress) =>
          views.html.mails.resetPassword(user, authToken)(frontRoute, contactAddress).toString
      }
  }

  case object UpdateEmailAddress extends EmailDefinition {
    override val category = Various

    override def examples =
      Seq("update_email_address" -> (recipient => build(recipient, dummyURL, daysBeforeExpiry = 2)))

    def build(recipient: EmailAddress, invitationUrl: URI, daysBeforeExpiry: Int): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(recipient)
        override val subject: String                = EmailSubjects.UPDATE_EMAIL_ADDRESS
        override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
          views.html.mails.updateEmailAddress(invitationUrl, daysBeforeExpiry).toString()
      }
  }

}
