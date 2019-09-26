package controllers.report

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import controllers.ReportController
import models._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.{Spec, Specification}
import org.specs2.matcher.Matcher
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.test._
import repositories.{EventRepository, ReportRepository, UserRepository}
import services.MailerService
import tasks.TasksModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.StatusPro._
import utils.Constants.{ActionEvent, Departments, StatusPro}
import utils.silhouette.auth.AuthEnv

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


object CreateReportFromNotEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          an outside experimentation department                         ${step(report = report.copy(companyPostalCode = Some(Departments.CollectivitesOutreMer(0))))}
         When create the report                                         ${step(createReport(report))}
         Then create the report with status "NA"                        ${reportMustHaveBeenCreatedWithStatus(StatusPro.NA)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report.copy(id = Some(reportUUID)), Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report.copy(statusPro = Some(StatusPro.NA)), Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
    """
}

object CreateReportForProWithoutAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has no account                             ${step(report = report.copy(companySiret = Some(siretForCompanyWithoutAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport(report))}
         Then create the report with status "A_TRAITER"                 ${reportMustHaveBeenCreatedWithStatus(StatusPro.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report.copy(id = Some(reportUUID)), Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And create an account for the professional                     ${accountToActivateMustHaveBeenCreated}
    """
}

object CreateReportForProWithNotActivatedAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has a not activated account                ${step(report = report.copy(companySiret = Some(siretForCompanyWithNotActivatedAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport(report))}
         Then create the report with status "A_TRAITER"                 ${reportMustHaveBeenCreatedWithStatus(StatusPro.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report.copy(id = Some(reportUUID)), Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
    """
}

object CreateReportForProWithActivatedAccountFromEligibleDepartment extends CreateReportSpec {
  override def is =
    s2"""
         Given a report which concerns
          a professional who has an activated account                   ${step(report = report.copy(companySiret = Some(siretForCompanyWithActivatedAccount)))}
          an experimentation department                                 ${step(report = report.copy(companyPostalCode = Some(Departments.AUTHORIZED(0))))}
         When create the report                                         ${step(createReport(report))}
         Then create the report with status "A_TRAITER"                 ${reportMustHaveBeenCreatedWithStatus(StatusPro.A_TRAITER)}
         And send a mail to admins                                      ${mailMustHaveBeenSent(contactEmail,"Nouveau signalement", views.html.mails.admin.reportNotification(report.copy(id = Some(reportUUID)), Nil)(FakeRequest().withBody(Json.toJson(report))).toString)}
         And send an acknowledgment mail to the consumer                ${mailMustHaveBeenSent(report.email,"Votre signalement", views.html.mails.consumer.reportAcknowledgment(report, Nil).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And do no create an account                                    ${accountMustNotHaveBeenCreated}
         And create an event "CONTACT_EMAIL"                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.CONTACT_EMAIL)}
         And send a mail to the pro                                     ${mailMustHaveBeenSent(proUser.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(report).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And update report status to "TRAITEMENT_EN_COURS"              ${reportMustHaveBeenUpdatedWithStatus(StatusPro.TRAITEMENT_EN_COURS)}
    """
}

trait CreateReportSpec extends Spec with CreateReportContext {

  import org.specs2.matcher.MatchersImplicits._
  import org.mockito.Matchers.{eq => eqTo, _}

  var report = reportFixture

  def createReport(report: Report) =  {
    Await.result(application.injector.instanceOf[ReportController].createReport().apply(FakeRequest().withBody(Json.toJson(report))), Duration.Inf)
  }

  def mailMustHaveBeenSent(recipient: String, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(application.injector.instanceOf[MailerService])
      .sendEmail(
        application.configuration.get[String]("play.mail.from"),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }

  def reportMustHaveBeenCreatedWithStatus(status: StatusProValue) = {
    there was one(mockReportRepository).create(argThat(reportStatusProMatcher(Some(status))))
  }

  def reportMustHaveBeenUpdatedWithStatus(status: StatusProValue) = {
    there was one(mockReportRepository).update(argThat(reportStatusProMatcher(Some(status))))
  }

  def reportStatusProMatcher(status: Option[StatusProValue]): Matcher[Report] = { report: Report =>
    (status == report.statusPro, s"status doesn't match ${status}")
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    there was one(mockEventRepository).createEvent(argThat(eventActionMatcher(action)))
  }

  def eventActionMatcher(action: ActionEventValue): Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def accountToActivateMustHaveBeenCreated = {
    there was one(mockUserRepository).create(
      argThat({ user: User =>
        (user.userRole == UserRoles.ToActivate && user.login == siretForCompanyWithoutAccount && user.activationKey.isDefined, "user doesn't match")
      })
    )
  }

  def accountMustNotHaveBeenCreated = {
    there was no(mockUserRepository).create(any[User])
  }


}

trait CreateReportContext extends Mockito {

  implicit val ec = ExecutionContext.global

  val contactEmail = "mail@test.fr"

  val siretForCompanyWithoutAccount = "00000000000000"
  val siretForCompanyWithNotActivatedAccount = "11111111111111"
  val siretForCompanyWithActivatedAccount = "22222222222222"

  val reportUUID = UUID.randomUUID();

  val reportFixture = Report(
    None, "category", List("subcategory"), List(), "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some("00000000000000"), Some(OffsetDateTime.now()),
    "firstName", "lastName", "email", true, List(), None, None
  )

  val toActivateUser = User(UUID.randomUUID(), siretForCompanyWithNotActivatedAccount, "code_activation", None, None, None, None, UserRoles.ToActivate)
  val proUser = User(UUID.randomUUID(), siretForCompanyWithActivatedAccount, "password", None, Some("Prénom"), Some("Nom"), Some("pro@signalconso.beta.gouv.fr"), UserRoles.Pro)

  val mockReportRepository = mock[ReportRepository]
  val mockEventRepository = mock[EventRepository]
  val mockUserRepository = mock[UserRepository]
  val mockMailerService = mock[MailerService]

  mockReportRepository.create(any[Report]) answers { report => Future(report.asInstanceOf[Report].copy(id = Some(reportUUID))) }
  mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }
  mockReportRepository.attachFilesToReport(any, any[UUID]) returns Future(0)
  mockReportRepository.retrieveReportFiles(any[UUID]) returns Future(Nil)

  mockUserRepository.create(any[User]) answers {user => Future(user.asInstanceOf[User])}
  mockUserRepository.findByLogin(siretForCompanyWithoutAccount) returns Future(None)
  mockUserRepository.findByLogin(siretForCompanyWithNotActivatedAccount) returns Future(Some(toActivateUser))
  mockUserRepository.findByLogin(siretForCompanyWithActivatedAccount) returns Future(Some(proUser))

  mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[ReportRepository].toInstance(mockReportRepository)
      bind[EventRepository].toInstance(mockEventRepository)
      bind[UserRepository].toInstance(mockUserRepository)
      bind[MailerService].toInstance(mockMailerService)
    }
  }

  lazy val application = new GuiceApplicationBuilder()
    .configure(
      Configuration(
        "play.evolutions.enabled" -> false,
        "slick.dbs.default.db.connectionPool" -> "disabled",
        "play.mailer.mock" -> true,
        "play.mail.contactRecipient" -> contactEmail
      )
    )
    .disable[TasksModule]
    .overrides(new FakeModule())
    .build()

}