package tasks.report

import models._
import models.company.Address
import models.report.ReportCategory
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

class NoReportNotification(implicit ee: ExecutionEnv) extends NoReportNotificationTaskSpec {
  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningTime, Period.ofDays(1)), Duration.Inf)
      }}
         And no email are sent to any users                           ${mailMustNotHaveBeenSent()}
    """
}

abstract class NoReportNotificationTaskSpec(implicit ee: ExecutionEnv)
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
  val covidDept   = "01"
  val tagDept     = "02"

  val covidEmail   = Fixtures.genEmailAddress("covid", "abo").sample.get
  val tagEmail     = Fixtures.genEmailAddress("tag", "abo").sample.get
  val countryEmail = Fixtures.genEmailAddress("tag", "abo").sample.get

  val covidSubscription = Subscription(
    userId = None,
    email = Some(covidEmail),
    departments = List(covidDept),
    categories = List(ReportCategory.Coronavirus),
    frequency = Period.ofDays(1)
  )

  val tagSubscription = Subscription(
    userId = None,
    email = Some(tagEmail),
    departments = List(tagDept),
    withTags = List(ReportTag.ProduitDangereux),
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
    .copy(
      companyAddress = Address(postalCode = Some(covidDept + "000")),
      category = ReportCategory.Coronavirus.entryName
    )
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
    there was no(mailRetriesService)
      .sendEmailWithRetries(
        any[EmailRequest]
      )
}
