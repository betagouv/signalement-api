package services.emails

import services.emails.EmailCategory.Dgccrf
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI

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

}
