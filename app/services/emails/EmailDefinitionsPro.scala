package services.emails

import models.User
import models.company.Company
import services.emails.EmailCategory.Pro
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute

import java.net.URI

object EmailDefinitionsPro {

  case object ProCompanyAccessInvitation extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq("access_invitation" -> (recipient => build(recipient, genCompany, dummyURL, None)))

    def build(recipient: EmailAddress, company: Company, invitationUrl: URI, invitedBy: Option[User]): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(recipient)
        override val subject: String                = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name)
        override def getBody: (FrontRoute, EmailAddress) => String =
          (_, _) => views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
      }
  }

}
