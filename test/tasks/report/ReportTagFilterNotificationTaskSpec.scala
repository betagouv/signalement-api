package tasks.report

import models._
import models.report.ReportTag
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
      "[SignalConso] [Produits dangereux] Un nouveau signalement",
      views.html.mails.dgccrf
        .reportNotification(tagSubscription, Seq(reportProduitDangereux), runningDate.minusDays(1))
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
    withTags = List(ReportTag.ProduitDangereux),
    frequency = Period.ofDays(1)
  )

  val company = Fixtures.genCompany.sample.get

  val reportProduitDangereux = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List(ReportTag.ProduitDangereux))

  val reportNoTag = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(companyAddress = Address(postalCode = Some(tagDept + "000")), tags = List())

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(reportProduitDangereux)
        _ <- reportRepository.create(reportNoTag)
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
