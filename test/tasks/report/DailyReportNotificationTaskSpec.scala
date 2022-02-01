package tasks.report

import models._
import models.report.ReportCategory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import repositories._
import services.AttachementService
import services.MailerService
import utils.Constants.Tags
import utils._

import java.time.LocalDate
import java.time.Period
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DailyReportNotification(implicit ee: ExecutionEnv) extends DailyReportNotificationTaskSpec {
  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
      Await.result(reportNotificationTask.runPeriodicNotificationTask(runningDate, Period.ofDays(1)), Duration.Inf)
    }}
         And a mail is sent to the user subscribed by category                           ${mailMustHaveBeenSent(
      Seq(covidEmail),
      s"[SignalConso] Un nouveau signalement",
      views.html.mails.dgccrf.reportNotification(covidSubscription, Seq(covidReport), runningDate.minusDays(1)).toString
    )}
         And a mail is sent to the user subscribed by tag                                ${mailMustHaveBeenSent(
      Seq(tagEmail),
      s"[SignalConso] [Produits dangereux] Un nouveau signalement",
      views.html.mails.dgccrf.reportNotification(tagSubscription, Seq(tagReport), runningDate.minusDays(1)).toString
    )}
         And a mail is sent to the user subscribed by country                            ${mailMustHaveBeenSent(
      Seq(countryEmail),
      s"[SignalConso] Un nouveau signalement",
      views.html.mails.dgccrf
        .reportNotification(countrySubscription, Seq(countryReport), runningDate.minusDays(1))
        .toString
    )}
    """
}

abstract class DailyReportNotificationTaskSpec(implicit ee: ExecutionEnv)
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

  val covidDept = "01"
  val tagDept = "02"

  val covidEmail = Fixtures.genEmailAddress("covid", "abo").sample.get
  val tagEmail = Fixtures.genEmailAddress("tag", "abo").sample.get
  val countryEmail = Fixtures.genEmailAddress("tag", "abo").sample.get

  val covidSubscription = Subscription(
    userId = None,
    email = Some(covidEmail),
    departments = List(covidDept),
    categories = List(ReportCategory.Covid),
    frequency = Period.ofDays(1)
  )

  val tagSubscription = Subscription(
    userId = None,
    email = Some(tagEmail),
    departments = List(tagDept),
    tags = List(Tags.DangerousProduct),
    frequency = Period.ofDays(1)
  )

  val countrySubscription = Subscription(
    userId = None,
    email = Some(countryEmail),
    countries = List(Country.Suisse),
    frequency = Period.ofDays(1)
  )

  val company = Fixtures.genCompany.sample.get
  val covidReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(covidDept + "000")), category = ReportCategory.Covid.value)
  val tagReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List(Tags.DangerousProduct))
  val countryReport =
    Fixtures.genReportForCompany(company).sample.get.copy(companyAddress = Address(country = Some(Country.Suisse)))

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(covidReport)
        _ <- reportRepository.create(tagReport)
        _ <- reportRepository.create(countryReport)
        _ <- subscriptionRepository.create(covidSubscription)
        _ <- subscriptionRepository.create(tagSubscription)
        _ <- subscriptionRepository.create(countrySubscription)
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
