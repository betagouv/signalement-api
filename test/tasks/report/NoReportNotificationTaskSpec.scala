package tasks.report

import models._
import models.report.ReportCategory
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import repositories._
import services.AttachementService
import services.MailerService
import models.report.ReportTag
import utils._
import play.api.libs.mailer.Attachment

import java.time.LocalDate
import java.time.Period
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class NoReportNotification(implicit ee: ExecutionEnv) extends NoReportNotificationTaskSpec {
  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningDate, Period.ofDays(1)), Duration.Inf)
      }}
         And no email are sent to any users                           ${mailMustNotHaveBeenSent()}
    """
}

abstract class NoReportNotificationTaskSpec(implicit ee: ExecutionEnv)
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
    tags = List(ReportTag.ProduitDangereux),
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
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List(ReportTag.ProduitDangereux))
  val countryReport =
    Fixtures.genReportForCompany(company).sample.get.copy(companyAddress = Address(country = Some(Country.Suisse)))

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- subscriptionRepository.create(covidSubscription)
        _ <- subscriptionRepository.create(tagSubscription)
        _ <- subscriptionRepository.create(countrySubscription)
      } yield (),
      Duration.Inf
    )

  def mailMustNotHaveBeenSent() =
    there was no(mailerService)
      .sendEmail(
        any[EmailAddress],
        any[Seq[EmailAddress]],
        any[Seq[EmailAddress]],
        anyString,
        anyString,
        any[Seq[Attachment]]
      )
}
