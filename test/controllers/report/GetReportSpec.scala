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
import repositories._
import services.MailerService
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, EventType, ReportStatus}
import utils.silhouette.auth.AuthEnv
import utils.{Address, EmailAddress}
import utils.Fixtures

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}


object GetReportByUnauthenticatedUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReport.id)))}
         Then user is not authorized                                  ${userMustBeUnauthorized}
    """
}

object GetReportByAdminUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step(someLoginInfo = Some(adminLoginInfo))}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReport.id)))}
         Then the report is rendered to the user as an Admin          ${reportMustBeRenderedForUserRole(neverRequestedReport, UserRoles.Admin)}
    """
}

object GetReportByNotConcernedProUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step(someLoginInfo = Some(notConcernedProLoginInfo))}
         When getting the report                                                ${step(someResult = Some(getReport(neverRequestedReport.id)))}
         Then the report is not found                                           ${reportMustBeNotFound}
    """
}

object GetReportByConcernedProUserFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report for the first time                          ${step(someResult = Some(getReport(neverRequestedReport.id)))}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPORT_READING_BY_PRO)}
         And the report reportStatusList is updated to "SIGNALEMENT_TRANSMIS"   ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.SIGNALEMENT_TRANSMIS)}
         And a mail is sent to the consumer                                     ${mailMustHaveBeenSent(neverRequestedReport.email,"L'entreprise a pris connaissance de votre signalement", views.html.mails.consumer.reportTransmission(neverRequestedReport).toString, mailerService.attachmentSeqForWorkflowStepN(3))}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(neverRequestedReport.copy(status = ReportStatus.SIGNALEMENT_TRANSMIS), UserRoles.Pro)}
      """
}

object GetFinalReportByConcernedProUserFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving a final report for the first time                      ${step(someResult = Some(getReport(neverRequestedFinalReport.id)))}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPORT_READING_BY_PRO)}
         And the report reportStatusList is not updated                         ${reportMustNotHaveBeenUpdated}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(neverRequestedFinalReport, UserRoles.Pro)}
    """
}

object GetReportByConcernedProUserNotFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report not for the first time                      ${step(someResult = Some(getReport(alreadyRequestedReport.id)))}
         Then no event is created                                               ${eventMustNotHaveBeenCreated}
         And the report reportStatusList is not updated                         ${reportMustNotHaveBeenUpdated}
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

  def reportMustBeNotFound() = {
    someResult must beSome and someResult.get.header.status === Status.NOT_FOUND
  }

  def reportMustBeRenderedForUserRole(report: Report, userRole: UserRole) = {
    implicit val someUserRole = Some(userRole)
    someResult must beSome and contentAsJson(Future(someResult.get)) === Json.toJson(ReportWithFiles(report, List.empty))
  }

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = Nil) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(application.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        Nil,
        subject,
        bodyHtml,
        attachments
      )
  }

  def mailMustNotHaveBeenSent() = {
    there was no(application.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(anyString),
        any[Seq[EmailAddress]],
        any[Seq[EmailAddress]],
        anyString,
        anyString,
        any
      )
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatusValue) = {
    there was one(mockReportRepository).update(argThat(reportStatusMatcher(status)))
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"reportStatusList doesn't match ${status}")
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

  val siretForConcernedPro = Fixtures.genSiret.sample.get
  val siretForNotConcernedPro = Fixtures.genSiret.sample.get

  val companyId = UUID.randomUUID
  val neverRequestedReportUUID = UUID.randomUUID
  val neverRequestedReport = Report(
    neverRequestedReportUUID, "category", List("subcategory"), List(), Some(companyId), Some("companyName"), Some(Address("companyAddress")), Some(Departments.ALL(0)), None,
    Some(siretForConcernedPro), None, OffsetDateTime.now(), "firstName", "lastName", EmailAddress("email"), true, false, TRAITEMENT_EN_COURS
  )

  val neverRequestedFinalReportUUID = UUID.randomUUID();
  val neverRequestedFinalReport = Report(
    neverRequestedFinalReportUUID, "category", List("subcategory"), List(), Some(companyId), Some("companyName"), Some(Address("companyAddress")), Some(Departments.ALL(0)), None,
    Some(siretForConcernedPro), None, OffsetDateTime.now(), "firstName", "lastName", EmailAddress("email"), true, false, SIGNALEMENT_CONSULTE_IGNORE
  )

  val alreadyRequestedReportUUID = UUID.randomUUID();
  val alreadyRequestedReport = Report(
    alreadyRequestedReportUUID, "category", List("subcategory"), List(), Some(companyId), Some("companyName"), Some(Address("companyAddress")), Some(Departments.ALL(0)), None,
    Some(siretForConcernedPro), None, OffsetDateTime.now(), "firstName", "lastName", EmailAddress("email"), true, false, SIGNALEMENT_TRANSMIS
  )

  val adminUser = Fixtures.genAdminUser.sample.get
  val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminUser.email.value)

  val concernedProUser = Fixtures.genProUser.sample.get
  val concernedProLoginInfo = LoginInfo(CredentialsProvider.ID, concernedProUser.email.value)

  val notConcernedProUser = Fixtures.genProUser.sample.get
  val notConcernedProLoginInfo = LoginInfo(CredentialsProvider.ID, notConcernedProUser.email.value)

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminUser, concernedProLoginInfo -> concernedProUser, notConcernedProLoginInfo -> notConcernedProUser))

  val mockReportRepository = mock[ReportRepository]
  val mockEventRepository = mock[EventRepository]
  val mockCompanyRepository = mock[CompanyRepository]
  val mockMailerService = mock[MailerService]
  lazy val mailerService = application.injector.instanceOf[MailerService]

  mockCompanyRepository.getUserLevel(companyId, concernedProUser) returns Future(AccessLevel.ADMIN)
  mockCompanyRepository.getUserLevel(companyId, notConcernedProUser) returns Future(AccessLevel.NONE)
  mockCompanyRepository.getUserLevel(companyId, adminUser) returns Future(AccessLevel.NONE)

  mockReportRepository.getReport(neverRequestedReport.id) returns Future(Some(neverRequestedReport))
  mockReportRepository.getReport(neverRequestedFinalReport.id) returns Future(Some(neverRequestedFinalReport))
  mockReportRepository.getReport(alreadyRequestedReport.id) returns Future(Some(alreadyRequestedReport))
  mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report])}
  mockReportRepository.retrieveReportFiles(any[UUID]) returns Future(List.empty)

  mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }
  mockEventRepository.getEvents(neverRequestedReport.id, EventFilter(None)) returns Future(List.empty)
  mockEventRepository.getEvents(neverRequestedFinalReport.id, EventFilter(None)) returns Future(List.empty)
  mockEventRepository.getEvents(alreadyRequestedReport.id, EventFilter(None)) returns Future(
    List(Event(Some(UUID.randomUUID()), Some(alreadyRequestedReport.id), Some(companyId), Some(concernedProUser.id), Some(OffsetDateTime.now()), EventType.PRO, ActionEvent.REPORT_READING_BY_PRO))
  )

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[Environment[AuthEnv]].toInstance(env)
      bind[ReportRepository].toInstance(mockReportRepository)
      bind[EventRepository].toInstance(mockEventRepository)
      bind[CompanyRepository].toInstance(mockCompanyRepository)
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
    .overrides(new FakeModule())
    .build()

}