package services.emails

import cats.data.NonEmptyList
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
import java.time.OffsetDateTime
import java.time.Period

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

  case object ProResponseAcknowledgmentOnAdminCompletion extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq(
        "report_ack_pro_on_admin_completion" -> (recipient =>
          build(genReport, List(genUser.copy(email = recipient), genUser, genUser))
        )
      )

    def build(theReport: Report, users: List[User]): Email =
      new ProFilteredEmailSingleReport {
        override val report: Report                 = theReport
        override val recipients: List[EmailAddress] = users.map(_.email)
        override val subject: String                = EmailSubjects.REPORT_ACK_PRO_ON_ADMIN_COMPLETION

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) => views.html.mails.professional.reportAcknowledgmentProOnAdminCompletion(frontRoute).toString

      }
  }

  case object ProNewReportNotification extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq(
        "report_notification" -> (recipient => build(NonEmptyList.of(recipient), genReport))
      )

    def build(userList: NonEmptyList[EmailAddress], theReport: Report): Email =
      new ProFilteredEmailSingleReport {
        override val report: Report                 = theReport
        override val subject: String                = EmailSubjects.NEW_REPORT
        override val recipients: List[EmailAddress] = userList.toList

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) => views.html.mails.professional.reportNotification(report)(frontRoute).toString

      }
  }

  case object ProReportReOpeningNotification extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq(
        "report_reopening_notification" -> (recipient => build(List(recipient), genReport))
      )

    def build(userList: List[EmailAddress], theReport: Report): Email =
      new ProFilteredEmailSingleReport {
        override val report: Report                 = theReport
        override val subject: String                = EmailSubjects.REPORT_REOPENING
        override val recipients: List[EmailAddress] = userList.toList

        override def getBody: (FrontRoute, EmailAddress) => String =
          (frontRoute, _) => views.html.mails.professional.reportReOpening(report)(frontRoute).toString
      }
  }

  case object ProReportsReadReminder extends EmailDefinition {
    override val category = Pro

    override def examples = {
      val report1 = genReport
      val report2 = genReport.copy(companyId = report1.companyId)
      val report3 = genReport.copy(companyId = report1.companyId, expirationDate = OffsetDateTime.now().plusDays(5))
      Seq(
        "reports_transmitted_reminder" -> (recipient =>
          EmailImpl(List(recipient), List(report1, report2, report3), Period.ofDays(7))
        )
      )
    }

    case class EmailImpl(
        recipients: List[EmailAddress],
        reports: List[Report],
        period: Period
    ) extends ProFilteredEmailMultipleReport {
      override val subject: String = EmailSubjects.REPORT_TRANSMITTED_REMINDER

      override def getBody: (FrontRoute, EmailAddress) => String =
        (frontRoute, _) =>
          views.html.mails.professional.reportsTransmittedReminder(reports, period)(frontRoute).toString
    }
  }

}
