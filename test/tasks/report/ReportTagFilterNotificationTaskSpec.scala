package tasks.report

import models._
import models.company.Address
import models.report.ReportTag
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import services.emails.MailRetriesService.EmailRequest
import utils._

import java.time.OffsetDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class DailyReporFilterWithTagNotification(implicit ee: ExecutionEnv) extends ReportTagFilterNotificationTaskSpec {

  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningTime, Period.ofDays(1)), Duration.Inf)
      }}
         And a mail is sent to the user subscribed by tag                                ${mailMustHaveBeenSent(
        Seq(EmailAddress("tag.abo.546438@example.com")),
        "[SignalConso] [Produit dangereux] Un nouveau signalement a été déposé",
        views.html.mails.dgccrf
          .reportNotification(tagSubscription, Seq((reportProduitDangereux, List.empty)), runningDate.minusDays(1))
          .toString
      )}
    """
}

class DailyReportFilterWithoutTagNotification(implicit ee: ExecutionEnv) extends ReportTagFilterNotificationTaskSpec {

  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningTime, Period.ofDays(1)), Duration.Inf)
      }}
         And a mail is sent to the user subscribed without tag                                ${mailMustHaveBeenSent(
        Seq(EmailAddress("notag.abo.263682@example.com")),
        "[SignalConso] Un nouveau signalement a été déposé",
        views.html.mails.dgccrf
          .reportNotification(noTagSubscription, Seq((reportNoTag, List.empty)), runningDate.minusDays(1))
          .toString
      )}
    """
}

abstract class ReportTagFilterNotificationTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  val (app, components) = TestApp.buildApp(
    None
  )

  lazy val subscriptionRepository = components.subscriptionRepository
  lazy val reportRepository       = components.reportRepository
  lazy val companyRepository      = components.companyRepository
  lazy val reportNotificationTask = components.reportNotificationTask
  lazy val mailRetriesService     = components.mailRetriesService
  lazy val attachementService     = components.attachmentService

  implicit lazy val frontRoute: utils.FrontRoute       = components.frontRoute
  implicit lazy val contactAddress: utils.EmailAddress = emailConfiguration.contactAddress

  implicit val ec: ExecutionContext = ee.executionContext

  val runningTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(1)
  val runningDate = runningTime.toLocalDate()
  val tagDept     = "02"

  val tagEmail   = Fixtures.genEmailAddress("tag", "abo").sample.get
  val noTagEmail = Fixtures.genEmailAddress("notag", "abo").sample.get

  val tagSubscription = Subscription(
    userId = None,
    email = Some(EmailAddress("tag.abo.546438@example.com")),
    departments = List(tagDept),
    withTags = List(ReportTag.ProduitDangereux),
    frequency = Period.ofDays(1)
  )

  val noTagSubscription = Subscription(
    userId = None,
    email = Some(EmailAddress("notag.abo.263682@example.com")),
    departments = List(tagDept),
    withoutTags = List(ReportTag.ProduitDangereux),
    frequency = Period.ofDays(1)
  )

  val company = Fixtures.genCompany.sample.get

  val reportProduitDangereux = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      companyAddress = Address(postalCode = Some(tagDept + "000")),
      tags = List(ReportTag.ProduitDangereux)
    )

  val reportNoTag = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      creationDate = reportProduitDangereux.creationDate,
      companyAddress = Address(postalCode = Some(tagDept + "000")),
      tags = List()
    )

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(reportProduitDangereux)
        _ <- reportRepository.create(reportNoTag)
        _ <- subscriptionRepository.create(tagSubscription)
        _ <- subscriptionRepository.create(noTagSubscription)

      } yield (),
      Duration.Inf
    )

  def mailMustHaveBeenSent(recipients: Seq[EmailAddress], subject: String, bodyHtml: String) =
    there was one(mailRetriesService).sendEmailWithRetries(
      argThat((emailRequest: EmailRequest) =>
        emailRequest.recipients.sortBy(_.value).toList == recipients.sortBy(_.value) &&
          emailRequest.subject === subject && emailRequest.bodyHtml === bodyHtml && emailRequest.attachments == attachementService.defaultAttachments
      )
    )
}
