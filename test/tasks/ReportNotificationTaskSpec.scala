package tasks

import java.net.URI
import java.time.LocalDate
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

class WeeklyReportNotification(implicit ee: ExecutionEnv) extends ReportNotificationTaskSpec {
  override def is =
    s2"""
         When weekly reportNotificationTask task run                                      ${step(Await.result(reportNotificationTask.runWeeklyNotificationTask(runningDate), Duration.Inf))}
         And no mail is sent to the subscribed user and office  - case no new report      ${not(mailMustHaveBeenSent(Seq(userWithoutReport.email), "Aucun nouveau signalement", views.html.mails.dgccrf.reportNotification(Seq.empty, department3, None, runningDate.minusDays(7)).toString))}
         And a mail is sent to the subscribed user and office  - case 1 new report        ${mailMustHaveBeenSent(Seq(officeEmail), s"[SignalConso] Un nouveau signalement pour le département $department2", views.html.mails.dgccrf.reportNotification(Seq(report2), department2, None, runningDate.minusDays(7)).toString)}
         And a mail is sent to the subscribed user and office  - case many new reports    ${mailMustHaveBeenSent(Seq(user.email, officeEmail), s"[SignalConso] 2 nouveaux signalements pour le département $department1", views.html.mails.dgccrf.reportNotification(Seq(report12, report11), department1, None, runningDate.minusDays(7)).toString)}
         And a mail is sent to the subscribed user and office  - case of Guadeloupe       ${mailMustHaveBeenSent(Seq(user.email), s"[SignalConso] Un nouveau signalement pour le département $guadeloupe", views.html.mails.dgccrf.reportNotification(Seq(reportGuadeloupe), guadeloupe, None, runningDate.minusDays(7)).toString)}
    """
}

class DailyReportNotification(implicit ee: ExecutionEnv) extends ReportNotificationTaskSpec {
  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step(Await.result(reportNotificationTask.runDailyNotificationTask(runningDate, Some(ReportCategory.COVID)), Duration.Inf))}
         And a mail is sent to the subscribed user and office                            ${mailMustHaveBeenSent(Seq(covidEmail), s"[SignalConso] Un nouveau signalement dans la catégorie COVID-19 (coronavirus) pour le département $covidDept", views.html.mails.dgccrf.reportNotification(Seq(covidReport), covidDept, Some(ReportCategory.COVID), runningDate.minusDays(1)).toString)}
    """
}



abstract class ReportNotificationTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

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
  val covidDept = "01"

  val officeEmail = Fixtures.genEmailAddress("directe", "limousin").sample.get
  val covidEmail = Fixtures.genEmailAddress("covid", "abo").sample.get

  val user = Fixtures.genDgccrfUser.sample.get
  val userWithoutReport = Fixtures.genDgccrfUser.sample.get
  val officeSubscription = Subscription(Some(UUID.randomUUID()), None, Some(officeEmail), List(department1, department2, martinique), List.empty)
  val userSubscription = Subscription(Some(UUID.randomUUID()), Some(user.id), None, List(department1, guadeloupe), List.empty)
  val userSubscriptionWithoutReport = Subscription(Some(UUID.randomUUID()), Some(userWithoutReport.id), None, List(department3), List.empty)
  val covidSubscription = Subscription(Some(UUID.randomUUID()), None, Some(covidEmail), List(covidDept), List(ReportCategory.COVID))

  val company = Fixtures.genCompany.sample.get
  val report11 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1 + "000"))
  val report12 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department1 + "000"))
  val report2 = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(department2 + "000"))
  val reportGuadeloupe = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(guadeloupe + "00"))
  val covidReport = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(covidDept + "000"), category = ReportCategory.COVID.value)

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
        _ <- reportRepository.create(covidReport)
        _ <- subscriptionRepository.create(userSubscription)
        _ <- subscriptionRepository.create(officeSubscription)
        _ <- subscriptionRepository.create(userSubscriptionWithoutReport)
        _ <- subscriptionRepository.create(covidSubscription)
      } yield Unit,
      Duration.Inf
    )
  }

  def mailMustHaveBeenSent(blindRecipients: Seq[EmailAddress], subject: String, bodyHtml: String) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq.empty,
        blindRecipients,
        subject,
        bodyHtml,
        null
      )
  }
}
