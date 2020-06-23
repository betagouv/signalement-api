package tasks

import java.net.URI
import java.time.{LocalDate, OffsetDateTime, Period}
import java.util.UUID

import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.Configuration
import repositories._
import services.MailerService
import utils.{AppSpec, EmailAddress, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WeeklyReportNotification(implicit ee: ExecutionEnv) extends WeeklyReportNotificationTaskSpec {
  override def is =
    s2"""
         When weekly reportNotificationTask task run                                      ${step(Await.result(reportNotificationTask.runPeriodicNotificationTask(runningDate, Period.ofDays(7)), Duration.Inf))}
         A mail is sent to the subscribed user                                            ${mailMustHaveBeenSent(Seq(user.email), s"[SignalConso] 3 nouveaux signalements", views.html.mails.dgccrf.reportNotification(userSubscription, Seq(report11, report12, reportGuadeloupe), runningDate.minusDays(7)).toString)}
         And a mail is sent to the subscribed office                                      ${mailMustHaveBeenSent(Seq(officeEmail), s"[SignalConso] 3 nouveaux signalements", views.html.mails.dgccrf.reportNotification(officeSubscription, Seq(report11, report12, report2), runningDate.minusDays(7)).toString)}
    """
}


abstract class WeeklyReportNotificationTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportNotificationTask = injector.instanceOf[ReportNotificationTask]
  lazy val mailerService = injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress = injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  implicit val ec = ee.executionContext

  val runningDate = LocalDate.now.plusDays(1)

  val department1 = "87"
  val department2 = "19"
  val department3 = "23"
  val guadeloupe = "971"
  val martinique = "972"

  val officeEmail = Fixtures.genEmailAddress("directe", "limousin").sample.get

  val user = Fixtures.genDgccrfUser.sample.get
  val officeSubscription = Subscription(UUID.randomUUID(), None, Some(officeEmail), List(department1, department2, martinique), List.empty, List.empty, Period.ofDays(7))
  val userSubscription = Subscription(UUID.randomUUID(), Some(user.id), None, List(department1, guadeloupe), List.empty, List.empty, Period.ofDays(7))
  val userSubscriptionWithoutReport = Subscription(UUID.randomUUID(), Some(user.id), None, List(department3), List.empty, List.empty, Period.ofDays(7))

  val company = Fixtures.genCompany.sample.get
  val report11 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1 + "000"), creationDate = OffsetDateTime.now.minusDays(1))
  val report12 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1 + "000"), creationDate = OffsetDateTime.now.minusDays(2))
  val report2 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department2 + "000"), creationDate = OffsetDateTime.now.minusDays(3))
  val reportGuadeloupe = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(guadeloupe + "00"), creationDate = OffsetDateTime.now.minusDays(4))

  override def setupData = {
    Await.result(
      for {
        _ <- userRepository.create(user)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(report11)
        _ <- reportRepository.create(report12)
        _ <- reportRepository.create(report2)
        _ <- reportRepository.create(reportGuadeloupe)
        _ <- subscriptionRepository.create(userSubscription)
        _ <- subscriptionRepository.create(officeSubscription)
        _ <- subscriptionRepository.create(userSubscriptionWithoutReport)
      } yield Unit,
      Duration.Inf
    )
  }

  def mailMustHaveBeenSent(recipients: Seq[EmailAddress], subject: String, bodyHtml: String) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        recipients,
        Nil,
        subject,
        bodyHtml,
        Nil
      )
  }
}
