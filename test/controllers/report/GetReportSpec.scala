package controllers.report

import java.time.OffsetDateTime
import java.util.UUID

import akka.util.Timeout
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportController
import models._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.Spec
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.mvc.Result
import play.api.test.Helpers.contentAsJson
import play.api.test._
import play.mvc.Http.Status
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository}
import services.MailerService
import tasks.TasksModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.StatusPro._
import utils.Constants.{ActionEvent, Departments, EventType, StatusPro}
import utils.silhouette.auth.AuthEnv

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}


object GetReportByUnauthenticatedUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then user is not authorized                                  ${userMustBeUnauthorized}
    """
}

object GetReportByAdminUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step(someLoginInfo = Some(adminLoginInfo))}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then the report is rendered to the user as an Admin          ${reportMustBeRenderedForUserRole(neverRequestedReport, UserRoles.Admin)}
    """
}

object GetReportByNotConcernedProUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step(someLoginInfo = Some(notConcernedProLoginInfo))}
         When getting the report                                                ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then user is not authorized                                            ${userMustBeUnauthorized}
    """
}

object GetReportByConcernedProUserFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report for the first time                          ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.ENVOI_SIGNALEMENT)}
         And the report status is updated to "SIGNALEMENT_TRANSMIS"             ${reportMustHaveBeenUpdatedWithStatus(StatusPro.SIGNALEMENT_TRANSMIS)}
         And a mail is sent to the consumer                                     ${mailMustHaveBeenSent(neverRequestedReport.email,"Votre signalement", views.html.mails.consumer.reportTransmission(neverRequestedReport).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(neverRequestedReport.copy(statusPro = Some(StatusPro.SIGNALEMENT_TRANSMIS)), UserRoles.Pro)}
    """
}

object GetReportByConcernedProUserNotFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report not for the first time                      ${step(someResult = Some(getReport(alreadyRequestedReportUUID)))}
         Then no event is created                                               ${eventMustNotHaveBeenCreated}
         And the report status is not updated                                   ${reportMustNotHaveBeenUpdated}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(alreadyRequestedReport, UserRoles.Pro)}

    """
}

trait GetReportSpec extends Spec with GetReportContext {

  import org.specs2.matcher.MatchersImplicits._

  implicit val ee = ExecutionEnv.fromGlobalExecutionContext
  implicit val timeout: Timeout = 30.seconds

  var someLoginInfo: Option[LoginInfo] = None
  var someResult: Option[Result] = None

  def getReport(reportUUID: UUID) =  {
    Await.result(
      application.injector.instanceOf[ReportController].getReport(reportUUID.toString)
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest())),
      Duration.Inf
    )
  }

  def userMustBeUnauthorized() = {
    someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
  }

  def reportMustBeRenderedForUserRole(report: Report, userRole: UserRole) = {

    implicit val reportWriter = userRole match {
      case UserRoles.Pro => Report.reportProWriter
      case _ => Report.reportWriter
    }

    someResult must beSome and contentAsJson(Future(someResult.get)) === Json.toJson(report)
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


  def mailMustNotHaveBeenSent() = {
    there was no(application.injector.instanceOf[MailerService]).sendEmail(anyString, anyString)(anyString, anyString, any)
  }

  def reportMustHaveBeenUpdatedWithStatus(status: StatusProValue) = {
    there was two(mockReportRepository).update(argThat(reportStatusProMatcher(Some(status)))) //TODO Must be only one time
  }

  def reportStatusProMatcher(status: Option[StatusProValue]): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.statusPro, s"status doesn't match ${status}")
  }

  def reportMustNotHaveBeenUpdated() = {
    there was no(mockReportRepository).update(any[Report])
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    there was one(mockEventRepository).createEvent(argThat(eventActionMatcher(action)))
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated() = {
    there was no(mockEventRepository).createEvent(any[Event])
  }

}

trait GetReportContext extends Mockito {

  implicit val ec = ExecutionContext.global

  val siretForConcernedPro = "000000000000000"
  val siretForNotConcernedPro = "11111111111111"

  val neverRequestedReportUUID = UUID.randomUUID();
  val neverRequestedReport = Report(
    Some(neverRequestedReportUUID), "category", List("subcategory"), List(), "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some(siretForConcernedPro), Some(OffsetDateTime.now()),
    "firstName", "lastName", "email", true, List(), None, None
  )

  val alreadyRequestedReportUUID = UUID.randomUUID();
  val alreadyRequestedReport = Report(
    Some(alreadyRequestedReportUUID), "category", List("subcategory"), List(), "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some(siretForConcernedPro), Some(OffsetDateTime.now()),
    "firstName", "lastName", "email", true, List(), None, None
  )

  val adminUser = User(UUID.randomUUID(), "admin@signalconso.beta.gouv.fr", "password", None, Some("Prénom"), Some("Nom"), Some("admin@signalconso.beta.gouv.fr"), UserRoles.Admin)
  val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminUser.login)

  val concernedProUser = User(UUID.randomUUID(), siretForConcernedPro, "password", None, Some("Prénom"), Some("Nom"), Some("pro@signalconso.beta.gouv.fr"), UserRoles.Pro)
  val concernedProLoginInfo = LoginInfo(CredentialsProvider.ID, concernedProUser.login)

  val notConcernedProUser = User(UUID.randomUUID(), siretForNotConcernedPro, "password", None, Some("Prénom"), Some("Nom"), Some("pro@signalconso.beta.gouv.fr"), UserRoles.Pro)
  val notConcernedProLoginInfo = LoginInfo(CredentialsProvider.ID, notConcernedProUser.login)

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminUser, concernedProLoginInfo -> concernedProUser, notConcernedProLoginInfo -> notConcernedProUser))

  val mockReportRepository = mock[ReportRepository]
  val mockEventRepository = mock[EventRepository]
  val mockMailerService = mock[MailerService]

  mockReportRepository.getReport(neverRequestedReportUUID) returns Future(Some(neverRequestedReport))
  mockReportRepository.getReport(alreadyRequestedReportUUID) returns Future(Some(alreadyRequestedReport))
  mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }

  mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }
  mockEventRepository.getEvents(neverRequestedReportUUID, EventFilter(None)) returns Future(List.empty)
  mockEventRepository.getEvents(alreadyRequestedReportUUID, EventFilter(None)) returns Future(
    List(Event(Some(UUID.randomUUID()), Some(alreadyRequestedReportUUID), concernedProUser.id, Some(OffsetDateTime.now()), EventType.PRO, ActionEvent.ENVOI_SIGNALEMENT, Some(true), None))
  )

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[Environment[AuthEnv]].toInstance(env)
      bind[ReportRepository].toInstance(mockReportRepository)
      bind[EventRepository].toInstance(mockEventRepository)
      bind[MailerService].toInstance(mockMailerService)
    }
  }

  lazy val application = new GuiceApplicationBuilder()
    .configure(
      Configuration(
        "play.evolutions.enabled" -> false,
        "slick.dbs.default.db.connectionPool" -> "disabled",
        "play.mailer.mock" -> true
      )
    )
    .disable[TasksModule]
    .overrides(new FakeModule())
    .build()

}