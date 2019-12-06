package controllers.report

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportController
import models._
import org.specs2.Specification
import org.specs2.matcher._
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.test._
import repositories._
import services.MailerService
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, ReportStatus}
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.EmailAddress
import utils.Fixtures

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import ExecutionContext.Implicits.global

object CreateReportFromNotEligibleDepartment extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          an outside experimentation department                         ${step(report = report.copy(companyPostalCode = Some(Departments.CollectivitesOutreMer(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with reportStatusList "NA"              ${reportMustHaveBeenCreatedWithStatus(ReportStatus.NA)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report.copy(status = Some(ReportStatus.NA)), Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}
object CreateReportForEmployeeConsumer extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          an experimentation department                                   ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
          an employee consumer                                            ${step(report = report.copy(employeeConsumer = true))}
         When create the report                                           ${step(createReport())}
         Then create the report with reportStatusList "EMPLOYEE_CONSUMER" ${reportMustHaveBeenCreatedWithStatus(ReportStatus.EMPLOYEE_REPORT)}
         And send a mail to admins                                        ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                  ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report.copy(status = Some(ReportStatus.EMPLOYEE_REPORT)), Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

object CreateReportForProWithoutAccountFromEligibleDepartment extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has no account                             ${step(report = report.copy(companySiret = Some(siretForCompanyWithoutAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with reportStatusList "A_TRAITER"       ${reportMustHaveBeenCreatedWithStatus(ReportStatus.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

object CreateReportForProWithActivatedAccountFromEligibleDepartment extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has an activated account                   ${step(report = report.copy(companySiret = Some(siretForCompanyWithActivatedAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with status "TRAITEMENT_EN_COURS"       ${reportMustHaveBeenCreatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And create an event "CONTACT_EMAIL"                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.CONTACT_EMAIL)}
         And send a mail to the pro                                     ${mailMustHaveBeenSent(proUser.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(report).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

object UpdateReportWithSiret extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a preexisting report
            with a new SIRET                                            ${step(report = existingReport.copy(companySiret = Some(existingCompany.siret)))}
         When the report is updated                                     ${step(updateReport(report))}
         Then the report contains company info                          ${checkReport(report.copy(
                                                                          companyId = Some(existingCompany.id),
                                                                          companySiret = Some(existingCompany.siret),
                                                                          status = Some(ReportStatus.TRAITEMENT_EN_COURS)
                                                                        ))}
    """
}

trait CreateUpdateReportSpec extends Specification with AppSpec with FutureMatchers {

  import org.specs2.matcher.MatchersImplicits._
  import org.mockito.ArgumentMatchers.{eq => eqTo, _}

  implicit val ec = ExecutionContext.global

  lazy val reportRepository = app.injector.instanceOf[ReportRepository]
  lazy val eventRepository = app.injector.instanceOf[EventRepository]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val companyAccessRepository = app.injector.instanceOf[CompanyAccessRepository]

  val contactEmail = EmailAddress("contact@signalconso.beta.gouv.fr")

  val siretForCompanyWithoutAccount = "00000000000000"
  val siretForCompanyWithActivatedAccount = "22222222222222"

  val existingReport = Report(
    Some(UUID.randomUUID()), "category", List("subcategory"), List(), None, "dummyCompany", "dummyAddress", Some(Departments.AUTHORIZED(0)), None, Some(OffsetDateTime.now()),
    "firstName", "lastName", EmailAddress("email@example.com"), true, false, List(), None
  )
  val existingCompany = Company(
    UUID.randomUUID(), siretForCompanyWithActivatedAccount, OffsetDateTime.now(),
    "Test", "42 rue du Tests", Some("37500")
  )
  val reportFixture = Report(
    None, "category", List("subcategory"), List(), None, "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some("00000000000000"), Some(OffsetDateTime.now()),
    "firstName", "lastName", EmailAddress("email@example.com"), true, false, List(), None
  )

  var report = reportFixture
  val proUser = Fixtures.genProUser.sample.get

  override def setupData = {
    Await.result(for {
      u <- userRepository.create(proUser)
      _ <- reportRepository.create(existingReport)
      c <- companyRepository.getOrCreate(existingCompany.siret, existingCompany)
      _ <- companyAccessRepository.setUserLevel(c, u, AccessLevel.ADMIN)
    } yield Unit,
    Duration.Inf)
  }

  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  val concernedAdminUser = Fixtures.genAdminUser.sample.get
  val concernedAdminLoginInfo = LoginInfo(CredentialsProvider.ID, concernedAdminUser.login)

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(
    concernedAdminLoginInfo -> concernedAdminUser
  ))

  def createReport() =  {
    implicit val someUserRole = None
    implicit val reportWriter = Json.writes[Report]
    Await.result(app.injector.instanceOf[ReportController].createReport().apply(FakeRequest().withBody(Json.toJson(report))), Duration.Inf)
  }

  def updateReport(reportData: Report) = {
    implicit val someUserRole = Some(concernedAdminUser.userRole)
    implicit val reportWriter = Json.writes[Report]
    Await.result(app.injector.instanceOf[ReportController].updateReport(reportData.id.get.toString).apply(
      FakeRequest()
      .withAuthenticator[AuthEnv](concernedAdminLoginInfo)
      .withBody(Json.toJson(reportData))), Duration.Inf)
  }

  def checkReport(reportData: Report) = {
    val dbReport = Await.result(reportRepository.getReport(reportData.id.get), Duration.Inf)
    dbReport.get must beEqualTo(reportData)
  }

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(app.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }

  def reportMustHaveBeenCreatedWithStatus(status: ReportStatusValue) = {
    val reports = Await.result(reportRepository.list, Duration.Inf).toList.filter(_.id != existingReport.id)
    reports.length must beEqualTo(1)
    val expectedReport = report.copy(
      id = reports.head.id,
      creationDate = reports.head.creationDate,
      companyId = reports.head.companyId,
      status = Some(status)
    )
    report = reports.head
    report.id must beSome
    report.creationDate must beSome
    report.companyId must beSome
    report must beEqualTo(expectedReport)
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.list, Duration.Inf).toList
    events.length must beEqualTo(1)
    events.head.action must beEqualTo(action)
  }
}
