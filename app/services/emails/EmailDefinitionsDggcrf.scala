package services.emails

import models.Subscription
import models.User
import models.report.Report
import models.report.ReportFile
import models.report.ReportTag
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
      Seq("access_link" -> ((recipient, _) => EmailImpl("DGCCRF")(recipient, dummyURL)))

    final case class EmailImpl(role: String)(recipient: EmailAddress, invitationUrl: URI) extends Email {
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
        "inactive_account_reminder" -> ((recipient, _) =>
          EmailImpl(genUser.copy(email = recipient), Some(LocalDate.now().plusDays(90)))
        )
      )

    final case class EmailImpl(
        user: User,
        expirationDate: Option[LocalDate]
    ) extends Email {
      override val recipients: Seq[EmailAddress] = List(user.email)
      override val subject: String               = EmailSubjects.INACTIVE_DGCCRF_ACCOUNT_REMINDER

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, _) =>
        views.html.mails.dgccrf.inactiveAccount(user.fullName, expirationDate)(frontRoute).toString
    }
  }

  case object DgccrfDangerousProductReportNotification extends EmailDefinition {
    override val category = Dgccrf

    override def examples =
      Seq(
        "report_dangerous_product_notification" -> ((recipient, _) => EmailImpl(Seq(recipient), genReport))
      )

    final case class EmailImpl(
        recipients: Seq[EmailAddress],
        report: Report
    ) extends Email {
      override val subject: String = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] "))

      override def getBody: (FrontRoute, EmailAddress) => String = (frontRoute, contact) =>
        views.html.mails.dgccrf.reportDangerousProductNotification(report)(frontRoute, contact).toString
    }
  }

  case object DgccrfReportNotification extends EmailDefinition {
    override val category = Dgccrf

    override def examples =
      Seq(
        "report_notif_dgccrf" -> ((recipient, _) =>
          build(
            List(recipient),
            genSubscription,
            List(
              (genReport, List(genReportFile)),
              (genReport.copy(tags = List(ReportTag.ReponseConso)), List(genReportFile))
            ),
            LocalDate.now().minusDays(10)
          )
        )
      )

    def build(
        theRecipients: List[EmailAddress],
        subscription: Subscription,
        reports: Seq[(Report, List[ReportFile])],
        startDate: LocalDate
    ): Email =
      new Email {
        override val recipients = theRecipients
        override val subject = EmailSubjects.REPORT_NOTIF_DGCCRF(
          reports.length,
          subscription.withTags.find(_ == ReportTag.ProduitDangereux).map(_ => "[Produits dangereux] ")
        )

        override def getBody = (frontRoute, contact) =>
          views.html.mails.dgccrf.reportNotification(subscription, reports, startDate)(frontRoute, contact).toString
      }
  }

  case object DgccrfValidateEmail extends EmailDefinition {
    override val category = Dgccrf

    override def examples =
      Seq(
        "validate_email" -> ((recipient, _) =>
          build(
            recipient,
            7,
            dummyURL
          )
        )
      )

    def build(
        email: EmailAddress,
        daysBeforeExpiry: Int,
        validationUrl: URI
    ): Email =
      new Email {
        override val recipients: List[EmailAddress] = List(email)
        override val subject: String                = EmailSubjects.VALIDATE_EMAIL

        override def getBody: (FrontRoute, EmailAddress) => String = (_, _) =>
          views.html.mails.validateEmail(validationUrl, daysBeforeExpiry).toString
      }
  }

}
