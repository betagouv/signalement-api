package tasks.report

import models._
import models.report.Tag.ReportTag
import models.report.Tag
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import repositories._
import services.AttachementService
import services.MailerService
import utils._

import java.time.LocalDate
import java.time.Period
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DailyReportWithFilterTagNotification(implicit ee: ExecutionEnv) extends ReportTagFilterNotificationTaskSpec {

  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningDate, Period.ofDays(1)), Duration.Inf)
      }}
         And a mail is sent to the user subscribed by tag                                ${mailMustHaveBeenSent(
        Seq(tagEmail),
        "[SignalConso] [Produits dangereux] 2 nouveaux signalements",
        views.html.mails.dgccrf
          .reportNotification(tagSubscription, Seq(tagReport, tagReport2), runningDate.minusDays(1))
          .toString
      )}
    """
}

abstract class ReportTagFilterNotificationTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportNotificationTask = injector.instanceOf[ReportNotificationTask]
  lazy val mailerService = injector.instanceOf[MailerService]
  lazy val attachementService = injector.instanceOf[AttachementService]

  implicit lazy val frontRoute = injector.instanceOf[FrontRoute]
  implicit lazy val contactAddress = emailConfiguration.contactAddress

  implicit val ec = ee.executionContext

  val runningDate = LocalDate.now.plusDays(1)

  val tagDept = "02"

  val tagEmail = Fixtures.genEmailAddress("tag", "abo").sample.get

  val tagSubscription = Subscription(
    userId = None,
    email = Some(tagEmail),
    departments = List(tagDept),
    tags = List(ReportTag.ProduitDangereux, Tag.NA),
    frequency = Period.ofDays(1)
  )

  val company = Fixtures.genCompany.sample.get

  val tagReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List(ReportTag.ProduitDangereux))

  val tagReport2 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List())

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(tagReport)
        _ <- reportRepository.create(tagReport2)
        _ <- subscriptionRepository.create(tagSubscription)

      } yield (),
      Duration.Inf
    )

  def mailMustHaveBeenSent(recipients: Seq[EmailAddress], subject: String, bodyHtml: String) =
    there was one(mailerService)
      .sendEmail(
        emailConfiguration.from,
        recipients,
        Nil,
        subject,
        bodyHtml,
        attachementService.defaultAttachments
      )
}
