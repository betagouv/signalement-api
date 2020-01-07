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
import play.api.libs.json.{Json, Writes}
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.mvc.Result
import play.api.test._
import play.mvc.Http.Status
import repositories._
import services.MailerService
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue
import utils.Constants.ReportStatus.{A_TRAITER, ReportStatusValue}
import utils.Constants.{ActionEvent, Departments, EventType, ReportStatus}
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress
import utils.Fixtures

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import org.apache.commons.mail.Email


object CreateEventByUnauthenticatedUser extends CreateEventSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When create an event                                         ${step(someResult = Some(createEvent()))}
         Then user is not authorized                                  ${userMustBeUnauthorized}
    """
}

object CreateEventAdminPostalMail extends CreateEventSpec {
  override def is =
    s2"""
        Given an authenticated admin user                                        ${step(someLoginInfo = Some(adminLoginInfo))}
        Given an action of sending postal letter                                 ${step(someEvent = Some(eventToCreate(EventType.PRO, ActionEvent.CONTACT_COURRIER)))}
        When create the associated event                                         ${step(someResult = Some(createEvent()))}
        Then an event "CONTACT_COURRIER" is created                              ${eventMustHaveBeenCreatedWithAction(ActionEvent.CONTACT_COURRIER)}
        And the report reportStatusList is updated to "TRAITEMENT_EN_COURS"      ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.TRAITEMENT_EN_COURS)}
        no email is sent                                                         ${mailMustNotHaveBeenSent}
    """
}

trait CreateEventSpec extends Spec with CreateEventContext {

  import org.specs2.matcher.MatchersImplicits._

  implicit val ee = ExecutionEnv.fromGlobalExecutionContext
  implicit val timeout: Timeout = 30.seconds

  implicit val actionEventValueWrites = new Writes[ActionEventValue] {
    def writes(actionEventValue: ActionEventValue) = Json.toJson(actionEventValue.value)
  }
  implicit val eventWriter = Json.writes[Event]

  var someLoginInfo: Option[LoginInfo] = None
  var someResult: Option[Result] = None
  var someEvent: Option[Event] = None

  def createEvent() =  {
    Await.result(
      application.injector.instanceOf[ReportController].createEvent(reportUUID.toString)
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest()).withBody(someEvent.map(Json.toJson(_)).getOrElse(Json.obj()))),
      Duration.Inf)
  }

  def userMustBeUnauthorized() = {
    someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    there was one(mockEventRepository).createEvent(argThat(eventActionMatcher(action)))
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatusValue) = {
    there was one(mockReportRepository).update(argThat(reportStatusMatcher(status)))
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"reportStatusList doesn't match ${status}")
  }

}

trait CreateEventContext extends Mockito {

  implicit val ec = ExecutionContext.global

  val reportUUID = UUID.randomUUID()

  val reportFixture = Report(
    reportUUID, "category", List("subcategory"), List(), None, "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some("00000000000000"), OffsetDateTime.now(),
    "firstName", "lastName", EmailAddress("toto@example.com"), true, false, A_TRAITER
  )

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(application.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(application.configuration.get[String]("play.mail.from")),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }

  def mailMustNotHaveBeenSent() = {
    there was no(application.injector.instanceOf[MailerService]).sendEmail(EmailAddress(anyString), EmailAddress(anyString))(anyString, anyString, any)
  }

  def eventToCreate(eventType: EventTypeValue, action: ActionEventValue) =
    Event(None, Some(reportUUID), Some(adminUser.id), None, eventType, action, Json.obj())

  val adminUser = Fixtures.genAdminUser.sample.get
  val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminUser.email.value)

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminUser))

  val mockReportRepository = mock[ReportRepository]
  val mockEventRepository = mock[EventRepository]
  val mockMailerService = mock[MailerService]
  val mockCompanyRepository = mock[CompanyRepository]
  val mockCompanyAccessRepository = mock[CompanyAccessRepository]
  val mockUserRepository = mock[UserRepository]

  mockReportRepository.getReport(reportUUID) returns Future(Some(reportFixture))
  mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }

  mockUserRepository.get(adminUser.id) returns Future(Some(adminUser))

  mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[Environment[AuthEnv]].toInstance(env)
      bind[ReportRepository].toInstance(mockReportRepository)
      bind[EventRepository].toInstance(mockEventRepository)
      bind[MailerService].toInstance(mockMailerService)
      bind[CompanyRepository].toInstance(mockCompanyRepository)
      bind[CompanyAccessRepository].toInstance(mockCompanyAccessRepository)
      bind[UserRepository].toInstance(mockUserRepository)
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
    .overrides(new FakeModule())
    .build()

}