package services.emails

import models.User
import services.emails.EmailCategory.Dgccrf
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI
import java.time.LocalDate

object EmailDefinitionsDggcrf {

  case object DgccrfAgentAccessLink extends EmailDefinition {
    override val category = Dgccrf

    override def examples =
      Seq("access_link" -> (recipient => build("DGCCRF")(recipient, dummyURL)))

    def build(role: String)(recipient: EmailAddress, invitationUrl: URI): Email =
      new Email {
        override val subject: String = EmailSubjects.DGCCRF_ACCESS_LINK
        override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
          views.html.mails.dgccrf.accessLink(invitationUrl, role).toString
        override val recipients: List[EmailAddress] = List(recipient)
      }
  }

  case object DgccrfInactiveAccount extends EmailDefinition {
    override val category = Dgccrf

    override def examples =
      Seq(
        "inactive_account_reminder" -> (recipient =>
          build(genUser.copy(email = recipient), Some(LocalDate.now().plusDays(90)))
        )
      )

    def build(user: User, expirationDate: Option[LocalDate]): Email =
      new Email {
        override val recipients: Seq[EmailAddress] = List(user.email)
        override val subject: String               = EmailSubjects.INACTIVE_DGCCRF_ACCOUNT_REMINDER
        override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
          views.html.mails.dgccrf.inactiveAccount(user.fullName, expirationDate)(frontRoute).toString
      }
  }

}
