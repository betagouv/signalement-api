package controllers.report

import java.time.OffsetDateTime
import java.util.UUID

import controllers.ReportController
import models._
import org.specs2.Specification
import org.specs2.matcher._
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.test._
import repositories.{EventRepository, ReportRepository, UserRepository}
import services.MailerService
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, ReportStatus}
import utils.silhouette.auth.AuthEnv
import utils.AppSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


object CreateReportFromNotEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          an outside experimentation department                         ${step(report = report.copy(companyPostalCode = Some(Departments.CollectivitesOutreMer(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with reportStatusList "NA"                        ${reportMustHaveBeenCreatedWithStatus(ReportStatus.NA)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report.copy(status = Some(ReportStatus.NA)), Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
    """
}

object CreateReportForProWithoutAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has no account                             ${step(report = report.copy(companySiret = Some(siretForCompanyWithoutAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with reportStatusList "A_TRAITER"                 ${reportMustHaveBeenCreatedWithStatus(ReportStatus.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And create an account for the professional                     ${accountToActivateMustHaveBeenCreated}
    """
}

object CreateReportForProWithNotActivatedAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has a not activated account                ${step(report = report.copy(companySiret = Some(siretForCompanyWithNotActivatedAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with reportStatusList "A_TRAITER"                 ${reportMustHaveBeenCreatedWithStatus(ReportStatus.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
    """
}

object CreateReportForProWithActivatedAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has an activated account                   ${step(report = report.copy(companySiret = Some(siretForCompanyWithActivatedAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport())}
         Then create the report with status "TRAITEMENT_EN_COURS"       ${reportMustHaveBeenCreatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report, Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
         And create an event "CONTACT_EMAIL"                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.CONTACT_EMAIL)}
         And send a mail to the pro                                     ${mailMustHaveBeenSent(proUser.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(report).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

trait CreateReportSpec extends Specification with AppSpec with FutureMatchers {

  import org.specs2.matcher.MatchersImplicits._
  import org.mockito.ArgumentMatchers.{eq => eqTo, _}

  val reportFixture = Report(
    None, "category", List("subcategory"), List(), "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some("00000000000000"), Some(OffsetDateTime.now()),
    "firstName", "lastName", "email", true, List(), None
  )

  var report = reportFixture

  lazy val reportRepository = app.injector.instanceOf[ReportRepository]
  lazy val eventRepository = app.injector.instanceOf[EventRepository]
  lazy val userRepository = app.injector.instanceOf[UserRepository]

  val contactEmail = "contact@signalconso.beta.gouv.fr"

  val siretForCompanyWithoutAccount = "00000000000000"
  val siretForCompanyWithNotActivatedAccount = "11111111111111"
  val siretForCompanyWithActivatedAccount = "22222222222222"

  val toActivateUser = User(UUID.randomUUID(), siretForCompanyWithNotActivatedAccount, "password", Some("code_activation"), None, None, None, UserRoles.ToActivate)
  val proUser = User(UUID.randomUUID(), siretForCompanyWithActivatedAccount, "password", None, Some("pro@signalconso.beta.gouv.fr"), Some("Prénom"), Some("Nom"), UserRoles.Pro)

  override def setupData = {
    userRepository.create(toActivateUser)
    userRepository.create(proUser)
  }

  def createReport() =  {
    Await.result(app.injector.instanceOf[ReportController].createReport().apply(FakeRequest().withBody(Json.toJson(report))), Duration.Inf)
  }

  def mailMustHaveBeenSent(recipient: String, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(app.injector.instanceOf[MailerService])
      .sendEmail(
        app.configuration.get[String]("play.mail.from"),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }

  def reportMustHaveBeenCreatedWithStatus(status: ReportStatusValue) = {
    val reports = Await.result(reportRepository.list, Duration.Inf).toList
    reports.length must beEqualTo(1)
    report = reports.head
    report.status must beEqualTo(Some(status))
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.list, Duration.Inf).toList
    events.length must beEqualTo(1)
    events.head.action must beEqualTo(action)
  }

  def accountToActivateMustHaveBeenCreated = {
    val users = Await.result(userRepository.list, Duration.Inf).toList
    users.length must beEqualTo(1)
    val user = users.head
    user.userRole must beEqualTo(UserRoles.ToActivate)
    user.login must beEqualTo(siretForCompanyWithoutAccount)
    user.activationKey must beSome
  }

  def accountMustNotHaveBeenCreated = {
    val users = Await.result(userRepository.list, Duration.Inf).toList
    users.map(_.id) must contain(exactly(toActivateUser.id, proUser.id))
  }
}
