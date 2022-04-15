package tasks.report

import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import repositories._
import repositories.company.CompanyRepository
import repositories.report.ReportRepository
import services.AttachementService
import services.MailerService
import utils._

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WeeklyReportNotification(implicit ee: ExecutionEnv) extends WeeklyReportNotificationTaskSpec {
  override def is =
    s2"""
      When weekly reportNotificationTask task run               ${step {
        Await.result(reportNotificationTask.runPeriodicNotificationTask(runningDate, Period.ofDays(7)), Duration.Inf)
      }}

    A mail is sent to the subscribed user                     ${mailMustHaveBeenSent(
        Seq(user.email),
        s"[SignalConso] 3 nouveaux signalements",
        views.html.mails.dgccrf
          .reportNotification(userSubscription, Seq(report11, report12, reportGuadeloupe), runningDate.minusDays(7))
          .toString
      )}
    A mail with reportCountry is sent to the subscribed user  ${mailMustHaveBeenSent(
        Seq(user.email),
        s"[SignalConso] Un nouveau signalement",
        views.html.mails.dgccrf
          .reportNotification(userSubscriptionCountries, Seq(reportArgentine), runningDate.minusDays(7))
          .toString
      )}
        And a mail is sent to the subscribed office               ${mailMustHaveBeenSent(
        Seq(officeEmail),
        s"[SignalConso] 3 nouveaux signalements",
        views.html.mails.dgccrf
          .reportNotification(officeSubscription, Seq(report11, report12, report2), runningDate.minusDays(7))
          .toString
      )}
      """

}

abstract class WeeklyReportNotificationTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
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

  val department1 = "87"
  val department2 = "19"
  val department3 = "23"
  val guadeloupe = "971"
  val martinique = "972"

  val officeEmail = Fixtures.genEmailAddress("directe", "limousin").sample.get

  val user = Fixtures.genDgccrfUser.sample.get
  val officeSubscription = Subscription(
    userId = None,
    email = Some(officeEmail),
    departments = List(department1, department2, martinique),
    categories = List.empty,
    withTags = List.empty,
    withoutTags = List.empty,
    countries = List.empty,
    sirets = List.empty,
    frequency = Period.ofDays(7)
  )

  val userSubscription = Subscription(
    userId = Some(user.id),
    email = None,
    departments = List(department1, guadeloupe),
    categories = List.empty,
    withTags = List.empty,
    withoutTags = List.empty,
    countries = List.empty,
    sirets = List.empty,
    frequency = Period.ofDays(7)
  )

  val userSubscriptionCountries = Subscription(
    userId = Some(user.id),
    email = None,
    departments = List.empty,
    categories = List.empty,
    withTags = List.empty,
    withoutTags = List.empty,
    countries = List(Country.Tunisie, Country.Argentine),
    sirets = List.empty,
    frequency = Period.ofDays(7)
  )

  val userSubscriptionWithoutReport = Subscription(
    userId = Some(user.id),
    email = None,
    departments = List(department3),
    categories = List.empty,
    withTags = List.empty,
    withoutTags = List.empty,
    countries = List.empty,
    sirets = List.empty,
    frequency = Period.ofDays(7)
  )

  val company = Fixtures.genCompany.sample.get
  val report11 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      companyAddress = Address(postalCode = Some(department1 + "000")),
      creationDate = OffsetDateTime.now.minusDays(1)
    )
  val report12 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      companyAddress = Address(postalCode = Some(department1 + "000")),
      creationDate = OffsetDateTime.now.minusDays(2)
    )
  val report2 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      companyAddress = Address(postalCode = Some(department2 + "000")),
      creationDate = OffsetDateTime.now.minusDays(3)
    )
  val reportGuadeloupe = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      companyAddress = Address(postalCode = Some(guadeloupe + "00")),
      creationDate = OffsetDateTime.now.minusDays(4)
    )
  val reportArgentine = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(country = Some(Country.Argentine)), creationDate = OffsetDateTime.now.minusDays(4))

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(user)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(report11)
        _ <- reportRepository.create(report12)
        _ <- reportRepository.create(report2)
        _ <- reportRepository.create(reportGuadeloupe)
        _ <- reportRepository.create(reportArgentine)
        _ <- subscriptionRepository.create(userSubscription)
        _ <- subscriptionRepository.create(userSubscriptionCountries)
        _ <- subscriptionRepository.create(officeSubscription)
        _ <- subscriptionRepository.create(userSubscriptionWithoutReport)
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
