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
      Seq("access_invitation" -> ((recipient, _) => EmailImpl(recipient, genCompany, dummyURL, None)))

    final case class EmailImpl(
        recipient: EmailAddress,
        company: Company,
        invitationUrl: URI,
        invitedBy: Option[User]
    ) extends Email {
      override val recipients: List[EmailAddress] = List(recipient)
      override val subject: String                = EmailSubjects.COMPANY_ACCESS_INVITATION(company.name)

      override def getBody: (FrontRoute, EmailAddress) => String =
        (_, _) => views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
    }

  }

  case object ProCompaniesAccessesInvitations extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq(
        "access_invitation_multiple_companies" -> ((recipient, _) =>
          EmailImpl(recipient, genCompanyList, genSiren, dummyURL)
        )
      )

    final case class EmailImpl(
        recipient: EmailAddress,
        companies: List[Company],
        siren: SIREN,
        invitationUrl: URI
    ) extends Email {
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
      Seq("new_company_access" -> ((recipient, _) => EmailImpl(recipient, genCompany, None)))

    final case class EmailImpl(
        recipient: EmailAddress,
        company: Company,
        invitedBy: Option[User]
    ) extends Email {
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
      Seq("new_companies_accesses" -> ((recipient, _) => EmailImpl(recipient, genCompanyList, genSiren)))

    final case class EmailImpl(
        recipient: EmailAddress,
        companies: List[Company],
        siren: SIREN
    ) extends Email {
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
      Seq(
        "report_ack_pro" -> ((recipient, _) => EmailImpl(genReport, genReportResponse, genUser.copy(email = recipient)))
      )

    final case class EmailImpl(report: Report, reportResponse: ReportResponse, user: User)
        extends ProFilteredEmailSingleReport {
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
        "report_ack_pro_on_admin_completion" -> ((recipient, _) =>
          EmailImpl(genReport, List(genUser.copy(email = recipient), genUser, genUser))
        )
      )

    final case class EmailImpl(report: Report, users: List[User]) extends ProFilteredEmailSingleReport {
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
        "report_notification" -> ((recipient, _) => EmailImpl(NonEmptyList.of(recipient), genReport))
      )

    final case class EmailImpl(userList: NonEmptyList[EmailAddress], report: Report)
        extends ProFilteredEmailSingleReport {
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
        "report_reopening_notification" -> ((recipient, _) => EmailImpl(List(recipient), genReport))
      )

    final case class EmailImpl(userList: List[EmailAddress], report: Report) extends ProFilteredEmailSingleReport {
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
        "reports_transmitted_reminder" -> ((recipient, _) =>
          EmailImpl(List(recipient), List(report1, report2, report3), Period.ofDays(7))
        )
      )
    }

    final case class EmailImpl(
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

  case object ProReportsUnreadReminder extends EmailDefinition {
    override val category = Pro

    override def examples = {
      val report1 = genReport
      val report2 = genReport.copy(companyId = report1.companyId)
      val report3 = genReport.copy(companyId = report1.companyId, expirationDate = OffsetDateTime.now().plusDays(5))

      Seq(
        "reports_unread_reminder" -> ((recipient, _) =>
          EmailImpl(List(recipient), List(report1, report2, report3), Period.ofDays(7))
        )
      )
    }

    final case class EmailImpl(
        recipients: List[EmailAddress],
        reports: List[Report],
        period: Period
    ) extends ProFilteredEmailMultipleReport {
      override val subject: String = EmailSubjects.REPORT_UNREAD_REMINDER

      override def getBody: (FrontRoute, EmailAddress) => String =
        (frontRoute, _) => views.html.mails.professional.reportsUnreadReminder(reports, period)(frontRoute).toString
    }
  }

  case object ProReportAssignedNotification extends EmailDefinition {
    override val category = Pro

    override def examples =
      Seq(
        "report_assignement_to_other" -> ((recipient, _) =>
          EmailImpl(report = genReport, assigningUser = genUser, assignedUser = genUser.copy(email = recipient))
        )
      )

    final case class EmailImpl(report: Report, assigningUser: User, assignedUser: User)
        extends ProFilteredEmailSingleReport {
      override val recipients: List[EmailAddress] = List(assignedUser.email)
      override val subject: String                = EmailSubjects.REPORT_ASSIGNED

      override def getBody: (FrontRoute, EmailAddress) => String = { (frontRoute, _) =>
        views.html.mails.professional.reportAssigned(report, assigningUser, assignedUser)(frontRoute).toString
      }
    }
  }

}
