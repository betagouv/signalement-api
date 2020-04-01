package tasks

import java.net.URI
import java.time.LocalDate
import java.util.UUID

import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.Configuration
import play.api.libs.mailer.Attachment
import repositories._
import services.MailerService
import utils.{AppSpec, EmailAddress, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DailyReportNotification(implicit ee: ExecutionEnv) extends DailyReportNotificationTaskSpec {
  override def is =
    s2"""
         When daily reportNotificationTask task run                                      ${step(Await.result(reportNotificationTask.runDailyNotificationTask(runningDate, Some(ReportCategory.COVID)), Duration.Inf))}
         And a mail is sent to the subscribed user                                       ${mailMustHaveBeenSent(Seq(covidEmail), s"[SignalConso] Un nouveau signalement dans la catégorie COVID-19 (coronavirus) pour le département $covidDept", views.html.mails.dgccrf.reportNotification(Seq(covidReport), covidDept, Some(ReportCategory.COVID), runningDate.minusDays(1)).toString)}
    """
}



abstract class DailyReportNotificationTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportNotificationTask = injector.instanceOf[ReportNotificationTask]
  lazy val mailerService = injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress = injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  implicit val ec = ee.executionContext

  val runningDate = LocalDate.now.plusDays(1)

  val covidDept = "01"

  val covidEmail = Fixtures.genEmailAddress("covid", "abo").sample.get

  val covidSubscription = Subscription(Some(UUID.randomUUID()), None, Some(covidEmail), List(covidDept), List(ReportCategory.COVID))

  val company = Fixtures.genCompany.sample.get
  val covidReport = Fixtures.genReportForCompany(company).sample.get.copy(companyPostalCode = Some(covidDept + "000"), category = ReportCategory.COVID.value)

  override def setupData = {
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(covidReport)
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
        bodyHtml
      )
  }
}
