package tasks

import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.Configuration
import repositories._
import services.MailerService
import utils.{AppSpec, EmailAddress, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration._

class ReportNotification(implicit ee: ExecutionEnv) extends ReportNotificationTaskSpec {
  override def is =
    s2"""
         When reportNotificationTask task run                                             ${step(Await.result(reportNotificationTask.runTask(runningDate), Duration.Inf))}
         And no mail is sent to the subscribed user and office  - case no new report      ${not(mailMustHaveBeenSent(List(user.email, officeEmail), "Aucun nouveau signalement", views.html.mails.dgccrf.reportOfTheWeek(Seq.empty, department3, runningDate.minusDays(7)).toString))}
         And a mail is sent to the subscribed user and office  - case 1 new report        ${mailMustHaveBeenSent(List(officeEmail), "Un nouveau signalement", views.html.mails.dgccrf.reportOfTheWeek(Seq(report2), department2, runningDate.minusDays(7)).toString)}
         And a mail is sent to the subscribed user and office  - case many new reports    ${mailMustHaveBeenSent(List(user.email, officeEmail), "2 nouveaux signalements", views.html.mails.dgccrf.reportOfTheWeek(Seq(report11, report12), department1, runningDate.minusDays(7)).toString)}
         And a mail is sent to the subscribed user and office  - case of Guadeloupe       ${mailMustHaveBeenSent(List(user.email), "Un nouveau signalement", views.html.mails.dgccrf.reportOfTheWeek(Seq(reportGuadeloupe), guadeloupe, runningDate.minusDays(7)).toString)}
    """
}

abstract class ReportNotificationTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportNotificationTask = injector.instanceOf[ReportNotificationTask]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = app.injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress = app.injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  implicit val ec = ee.executionContext

  val runningDate = LocalDate.now.plusDays(1)

  val department1 = "87"
  val department2 = "19"
  val department3 = "23"
  val guadeloupe = "971"
  val martinique = "972"

  val officeEmail = Fixtures.genEmailAddress("directe", "limousin").sample.get

  val user = Fixtures.genDgccrfUser.sample.get
  val userWithoutReport = Fixtures.genDgccrfUser.sample.get
  val officeSubscription = Subscription(Some(UUID.randomUUID()), None, Some(officeEmail), "Departments", List(department1, department2, department3, martinique))
  val userSubscription = Subscription(Some(UUID.randomUUID()), Some(user.id), None, "Departments", List(department1, department3, guadeloupe))

  val company = Fixtures.genCompany.sample.get
  val report11 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1))
  val report12 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1))
  val report2 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department2))
  val reportGuadeloupe = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(guadeloupe))

  override def setupData = {
    Await.result(
      for {
        _ <- userRepository.create(user)
        _ <- userRepository.create(userWithoutReport)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(report11)
        _ <- reportRepository.create(report12)
        _ <- reportRepository.create(report2)
        _ <- reportRepository.create(reportGuadeloupe)
        _ <- subscriptionRepository.create(userSubscription)
        _ <- subscriptionRepository.create(officeSubscription)
      } yield Unit,
      Duration.Inf
    )
  }

  def mailMustHaveBeenSent(recipients: List[EmailAddress], subject: String, bodyHtml: String) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        recipients: _*
      )(subject, bodyHtml)
  }
}
