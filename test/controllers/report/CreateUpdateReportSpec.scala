package controllers.report

import java.net.URI
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportController
import models._
import org.specs2.Specification
import org.specs2.matcher._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.mailer.Attachment
import play.api.test._
import repositories._
import services.MailerService
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, ReportStatus}
import utils.{AppSpec, EmailAddress, Fixtures}
import utils.silhouette.auth.AuthEnv

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object CreateReportFromDomTom extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a draft report which concerns
          a dom tom department                                              ${step(draftReport = draftReport.copy(companyPostalCode = Departments.CollectivitesOutreMer(0)))}
         When create the report                                             ${step(createReport())}
         Then create the report with reportStatusList "TRAITEMENT_EN_COURS" ${reportMustHaveBeenCreatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
         And send a mail to admins                                          ${mailMustHaveBeenSent(contactEmail,s"Nouveau signalement [${draftReport.category}]", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                    ${mailMustHaveBeenSent(draftReport.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, mailerService.attachmentSeqForWorkflowStepN(2))}
    """
}
object CreateReportForEmployeeConsumer extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a draft report which concerns
          an experimentation department                                   ${step(draftReport = draftReport.copy(companyPostalCode = Departments.ALL(0)))}
          an employee consumer                                            ${step(draftReport = draftReport.copy(employeeConsumer = true))}
         When create the report                                           ${step(createReport())}
         Then create the report with reportStatusList "EMPLOYEE_CONSUMER" ${reportMustHaveBeenCreatedWithStatus(ReportStatus.EMPLOYEE_REPORT)}
         And send a mail to admins                                        ${mailMustHaveBeenSent(contactEmail,s"Nouveau signalement [${draftReport.category}]", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                  ${mailMustHaveBeenSent(draftReport.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, mailerService.attachmentSeqForWorkflowStepN(2))}
    """
}

object CreateReportForProWithoutAccount extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a draft report which concerns
          a professional who has no account                                   ${step(draftReport = draftReport.copy(companySiret = anotherCompany.siret))}
         When create the report                                               ${step(createReport())}
         Then create the report with reportStatusList "TRAITEMENT_EN_COURS"   ${reportMustHaveBeenCreatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
         And send a mail to admins                                            ${mailMustHaveBeenSent(contactEmail,s"Nouveau signalement [${draftReport.category}]", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And no event is created                                              ${eventMustNotHaveBeenCreated(report.id, List.empty)}
         And send an acknowledgment mail to the consumer                      ${mailMustHaveBeenSent(draftReport.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, mailerService.attachmentSeqForWorkflowStepN(2))}
    """
}

object CreateReportForProWithActivatedAccount extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a draft report which concerns
          a professional who has an activated account                   ${step(draftReport = draftReport.copy(companySiret = existingCompany.siret))}
         When create the report                                         ${step(createReport())}
         Then create the report with status "TRAITEMENT_EN_COURS"       ${reportMustHaveBeenCreatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,s"Nouveau signalement [${draftReport.category}]", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest()).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(draftReport.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, mailerService.attachmentSeqForWorkflowStepN(2))}
         And create an event "CONTACT_EMAIL"                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.CONTACT_EMAIL)}
         And send a mail to the pro                                     ${mailMustHaveBeenSent(proUser.email,"Nouveau signalement", views.html.mails.professional.reportNotification(report).toString)}
    """
}

object UpdateReportConsumer extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a preexisting report                                     ${step(report = existingReport)}
         When the report consumer is updated                            ${step(updateReportConsumer(report.id, reportConsumer))}
         Then the report contains updated info                          ${checkReport(report.copy(
                                                                          firstName = reportConsumer.firstName,
                                                                          lastName = reportConsumer.lastName,
                                                                          email = reportConsumer.email,
                                                                          contactAgreement = reportConsumer.contactAgreement
                                                                        ))}
    """
}

object UpdateReportCompanySameSiret extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a preexisting report                                     ${step(report = existingReport)}
         When the report company is updated with same Siret             ${step(updateReportCompany(report.id, reportCompanySameSiret))}
         Then the report contains updated info                          ${checkReport(report.copy(
                                                                          companyName = reportCompanySameSiret.name,
                                                                          companyAddress = reportCompanySameSiret.address,
                                                                          companyPostalCode = Some(reportCompanySameSiret.postalCode),
                                                                          companySiret = Some(reportCompanySameSiret.siret)
                                                                        ))}
    """
}

object UpdateReportCompanyAnotherSiret extends CreateUpdateReportSpec {
  override def is =
    s2"""
         Given a preexisting report                                     ${step(report = existingReport)}
         When the report company is updated with same Siret             ${step(updateReportCompany(report.id, reportCompanyAnotherSiret))}
         Then the report contains updated info and the status is reset  ${checkReport(report.copy(
                                                                          companyId = Some(anotherCompany.id),
                                                                          companyName = reportCompanyAnotherSiret.name,
                                                                          companyAddress = reportCompanyAnotherSiret.address,
                                                                          companyPostalCode = Some(reportCompanyAnotherSiret.postalCode),
                                                                          companySiret = Some(reportCompanyAnotherSiret.siret),
                                                                          status = ReportStatus.TRAITEMENT_EN_COURS
                                                                        ))}
    """
}

trait CreateUpdateReportSpec extends Specification with AppSpec with FutureMatchers {

  implicit val ec = ExecutionContext.global

  lazy val reportRepository = app.injector.instanceOf[ReportRepository]
  lazy val eventRepository = app.injector.instanceOf[EventRepository]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = app.injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress = app.injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  val contactEmail = EmailAddress("contact@signal.conso.gouv.fr")

  val existingCompany = Fixtures.genCompany.sample.get
  val anotherCompany = Fixtures.genCompany.sample.get

  val existingReport = Fixtures.genReportForCompany(existingCompany).sample.get.copy(status = ReportStatus.NA)

  var draftReport = Fixtures.genDraftReport.sample.get.copy()
  var report = draftReport.generateReport
  val proUser = Fixtures.genProUser.sample.get

  val concernedAdminUser = Fixtures.genAdminUser.sample.get
  val concernedAdminLoginInfo = LoginInfo(CredentialsProvider.ID, concernedAdminUser.email.value)

  val reportConsumer = Fixtures.genReportConsumer.sample.get
  val reportCompanySameSiret = Fixtures.genReportCompany.sample.get.copy(siret = existingCompany.siret)
  val reportCompanyAnotherSiret = Fixtures.genReportCompany.sample.get.copy(siret = anotherCompany.siret, postalCode = "45000")

  override def setupData = {
    Await.result(for {
      u <- userRepository.create(proUser)
      _ <- userRepository.create(concernedAdminUser)
      c <- companyRepository.getOrCreate(existingCompany.siret, existingCompany)
      _ <- companyRepository.getOrCreate(anotherCompany.siret, anotherCompany)
      _ <- reportRepository.create(existingReport)
      _ <- companyRepository.setUserLevel(c, u, AccessLevel.ADMIN)
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

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(
    concernedAdminLoginInfo -> concernedAdminUser
  ))

  def createReport() =  {
    Await.result(app.injector.instanceOf[ReportController].createReport().apply(FakeRequest().withBody(Json.toJson(draftReport))), Duration.Inf)
  }

  def updateReportCompany(reportId: UUID, reportCompany: ReportCompany) = {
    Await.result(app.injector.instanceOf[ReportController].updateReportCompany(reportId.toString).apply(
      FakeRequest()
      .withAuthenticator[AuthEnv](concernedAdminLoginInfo)
      .withBody(Json.toJson(reportCompany))), Duration.Inf)
  }

  def updateReportConsumer(reportId: UUID, reportConsumer: ReportConsumer) = {
    Await.result(app.injector.instanceOf[ReportController].updateReportConsumer(reportId.toString).apply(
      FakeRequest()
      .withAuthenticator[AuthEnv](concernedAdminLoginInfo)
      .withBody(Json.toJson(reportConsumer))), Duration.Inf)
  }

  def checkReport(reportData: Report) = {
    val dbReport = Await.result(reportRepository.getReport(reportData.id), Duration.Inf)
    dbReport.get must beEqualTo(reportData)
  }

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        null,
        subject,
        bodyHtml,
        attachments
      )
  }

  def reportMustHaveBeenCreatedWithStatus(status: ReportStatusValue) = {
    val reports = Await.result(reportRepository.list, Duration.Inf).toList.filter(_.id != existingReport.id)
    val expectedReport = draftReport.generateReport.copy(
      id = reports.head.id,
      creationDate = reports.head.creationDate,
      companyId = reports.head.companyId,
      status = status
    )
    report = reports.head
    reports.length must beEqualTo(1) and
      (report.companyId must beSome) and
      (report must beEqualTo(expectedReport))
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.list, Duration.Inf).toList
    events.length must beEqualTo(1) and
      (events.head.action must beEqualTo(action))
  }

  def eventMustNotHaveBeenCreated(reportUUID: UUID, existingEvents: List[Event]) = {
    val events = Await.result(eventRepository.getEvents(None, Some(reportUUID), EventFilter()), Duration.Inf)
    events.length must beEqualTo(existingEvents.length)
  }
}
