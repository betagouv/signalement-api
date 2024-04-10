package services.emails

import services.emails.EmailCategory.Admin
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI

object EmailDefinitionsAdmin {

  case object AdminAccessLink extends EmailDefinition {
    override val category = Admin

    override def examples =
      Seq("access_link" -> (recipient => build(recipient, dummyURL)))

    def build(recipient: EmailAddress, invitationUrl: URI): Email =
      new Email {
        override val subject: String = EmailSubjects.ADMIN_ACCESS_LINK

        override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
          views.html.mails.admin.accessLink(invitationUrl).toString

        override val recipients: List[EmailAddress] = List(recipient)
      }
  }

  case object AdminProbeTriggered extends EmailDefinition {
    override val category = Admin

    override def examples =
      Seq(
        "probe_triggered" -> (recipient =>
          build(Seq(recipient), "Taux de schtroumpfs pas assez schtroumpfés", 0.2, "bas")
        )
      )

    def build(theRecipients: Seq[EmailAddress], probeName: String, rate: Double, issue: String): Email =
      new Email {
        override val recipients      = theRecipients
        override val subject: String = EmailSubjects.ADMIN_PROBE_TRIGGERED

        override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
          views.html.mails.admin.probetriggered(probeName, rate, issue).toString()
      }
  }

}
