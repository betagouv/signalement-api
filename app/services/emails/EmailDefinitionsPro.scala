package services.emails

import models.User
import models.company.Company
import models.report.Report
import models.report.ReportResponse
import services.emails.EmailCategory.Pro
import services.emails.EmailsExamplesUtils._
import utils.EmailAddress
import utils.EmailSubjects
import utils.FrontRoute
import utils.SIREN

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

  case object ProCompaniesAccessesInvitations extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq("access_invitation_multiple_companies" -> (recipient => build(recipient, genCompanyList, genSiren, dummyURL)))

    def build(recipient: EmailAddress, companies: List[Company], siren: SIREN, invitationUrl: URI): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(recipient)
        override val subject: String                = EmailSubjects.PRO_COMPANIES_ACCESSES_INVITATIONS(siren)

        override def getBody: (FrontRoute, EmailAddress) => String =
          (_, _) =>
            views.html.mails.professional.companiesAccessesInvitations(invitationUrl, companies, siren.value).toString
      }
  }

  case object ProNewCompanyAccess extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq("new_company_access" -> (recipient => build(recipient, genCompany, None)))

    def build(recipient: EmailAddress, company: Company, invitedBy: Option[User]): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(recipient)
        override val subject: String                = EmailSubjects.NEW_COMPANY_ACCESS(company.name)

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) =>
            views.html.mails.professional
              .newCompanyAccessNotification(frontRoute.dashboard.login, company, invitedBy)(frontRoute)
              .toString
      }
  }

  case object ProNewCompaniesAccesses extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq("new_companies_accesses" -> (recipient => build(recipient, genCompanyList, genSiren)))

    def build(recipient: EmailAddress, companies: List[Company], siren: SIREN): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(recipient)
        override val subject: String                = EmailSubjects.PRO_NEW_COMPANIES_ACCESSES(siren)

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) =>
            views.html.mails.professional
              .newCompaniesAccessesNotification(frontRoute.dashboard.login, companies, siren.value)(frontRoute)
              .toString
      }
  }

  case object ProResponseAcknowledgment extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq("report_ack_pro" -> (recipient => build(genReport, genReportResponse, genUser.copy(email = recipient))))

    def build(theReport: Report, reportResponse: ReportResponse, user: User): Email =
      new ProFilteredEmailSingleReport {
        override val report: Report                 = theReport
        override val recipients: List[EmailAddress] = List(user.email)
        override val subject: String                = EmailSubjects.REPORT_ACK_PRO

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) =>
            views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user)(frontRoute).toString

      }
  }

}