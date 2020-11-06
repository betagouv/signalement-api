package controllers.report

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportController
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.libs.json.Json
import play.api.libs.mailer.Attachment
import play.api.mvc.Result
import play.api.test._
import play.mvc.Http.Status
import repositories._
import services.MailerService
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus.{ReportStatusValue, SIGNALEMENT_TRANSMIS}
import utils.Constants.{ActionEvent, ReportStatus}
import utils.{AppSpec, EmailAddress, Fixtures}
import utils.silhouette.auth.AuthEnv

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class ReportResponseByUnauthenticatedUser(implicit ee: ExecutionEnv) extends ReportResponseSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When post a response                                         ${step(someResult = Some(postReportResponse(reportResponseAccepted)))}
         Then result status is not authorized                         ${resultStatusMustBe(Status.UNAUTHORIZED)}
    """
}

class ReportResponseByNotConcernedProUser(implicit ee: ExecutionEnv) extends ReportResponseSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step(someLoginInfo = Some(notConcernedProLoginInfo))}
         When post a response                                                   ${step(someResult = Some(postReportResponse(reportResponseAccepted)))}
         Then result status is not found                                        ${resultStatusMustBe(Status.NOT_FOUND)}
    """
}

class ReportResponseProAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step(someLoginInfo = Some(concernedProLoginInfo))}
        When post a response with type "ACCEPTED"                                ${step(someResult = Some(postReportResponse(reportResponseAccepted)))}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPORT_PRO_RESPONSE)}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE)}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT)}
        And the response files are attached to the report                        ${reportFileMustHaveBeenAttachedToReport()}
        And the report reportStatusList is updated to "PROMESSE_ACTION"          ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.PROMESSE_ACTION)}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(reportFixture.email,"L'entreprise a répondu à votre signalement", views.html.mails.consumer.reportToConsumerAcknowledgmentPro(report, reportResponseAccepted, reviewUrl).toString, mailerService.attachmentSeqForWorkflowStepN(4))}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(concernedProUser.email,"Votre réponse au signalement", views.html.mails.professional.reportAcknowledgmentPro(reportResponseAccepted, concernedProUser).toString)}
    """
}

class ReportResponseProRejectedAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step(someLoginInfo = Some(concernedProLoginInfo))}
        When post a response with type "REJECTED"                                ${step(someResult = Some(postReportResponse(reportResponseRejected)))}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPORT_PRO_RESPONSE)}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE)}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT)}
        And the report reportStatusList is updated to "SIGNALEMENT_INFONDE"      ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.SIGNALEMENT_INFONDE)}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(reportFixture.email,"L'entreprise a répondu à votre signalement", views.html.mails.consumer.reportToConsumerAcknowledgmentPro(report, reportResponseRejected, reviewUrl).toString, mailerService.attachmentSeqForWorkflowStepN(4))}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(concernedProUser.email,"Votre réponse au signalement", views.html.mails.professional.reportAcknowledgmentPro(reportResponseRejected, concernedProUser).toString)}
    """
}

class ReportResponseProNotConcernedAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step(someLoginInfo = Some(concernedProLoginInfo))}
        When post a response with type "NOT_CONCERNED"                           ${step(someResult = Some(postReportResponse(reportResponseNotConcerned)))}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPORT_PRO_RESPONSE)}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE)}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT)}
        And the report reportStatusList is updated to "MAL_ATTRIBUE"             ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.SIGNALEMENT_MAL_ATTRIBUE)}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(reportFixture.email,"L'entreprise a répondu à votre signalement", views.html.mails.consumer.reportToConsumerAcknowledgmentPro(report, reportResponseNotConcerned, reviewUrl).toString, mailerService.attachmentSeqForWorkflowStepN(4))}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(concernedProUser.email,"Votre réponse au signalement", views.html.mails.professional.reportAcknowledgmentPro(reportResponseNotConcerned, concernedProUser).toString)}
    """
}

abstract class ReportResponseSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val reportRepository = app.injector.instanceOf[ReportRepository]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val eventRepository = app.injector.instanceOf[EventRepository]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = app.injector.instanceOf[AccessTokenRepository]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  val contactEmail = EmailAddress("contact@signal.conso.gouv.fr")

  val siretForConcernedPro = Fixtures.genSiret.sample.get
  val siretForNotConcernedPro = Fixtures.genSiret.sample.get

  val companyData = Fixtures.genCompany.sample.get.copy(siret = siretForConcernedPro)

  val reportFixture = Fixtures.genReportForCompany(companyData).sample.get.copy(status = SIGNALEMENT_TRANSMIS)

  var reviewUrl = new URI("")
  var report = reportFixture

  val concernedProUser = Fixtures.genProUser.sample.get
  val concernedProLoginInfo = LoginInfo(CredentialsProvider.ID, concernedProUser.email.value)

  val notConcernedProUser = Fixtures.genProUser.sample.get
  val notConcernedProLoginInfo = LoginInfo(CredentialsProvider.ID, notConcernedProUser.email.value)

  var someLoginInfo: Option[LoginInfo] = None
  var someResult: Option[Result] = None

  val reportResponseFile = ReportFile(UUID.randomUUID(), None, OffsetDateTime.now, "fichier.jpg", "123_fichier.jpg", ReportFileOrigin.PROFESSIONAL, None)

  val reportResponseAccepted = ReportResponse(ReportResponseType.ACCEPTED, "details for consumer", Some("details for dgccrf"), List(reportResponseFile.id))
  val reportResponseRejected = ReportResponse(ReportResponseType.REJECTED, "details for consumer", Some("details for dgccrf"), List.empty)
  val reportResponseNotConcerned = ReportResponse(ReportResponseType.NOT_CONCERNED, "details for consumer", Some("details for dgccrf"), List.empty)

  override def setupData = {
    reviewUrl = app.configuration.get[URI]("play.website.url").resolve(s"/suivi-des-signalements/${reportFixture.id}/avis")
    Await.result(
      for {
        company <- companyRepository.getOrCreate(companyData.siret, companyData)
        admin   <- userRepository.create(concernedProUser)
        _       <- companyRepository.setUserLevel(company, admin, AccessLevel.ADMIN)
        _       <- userRepository.create(notConcernedProUser)
        _       <- reportRepository.create(reportFixture)
        -       <- reportRepository.createFile(reportResponseFile)
      } yield Unit,
      Duration.Inf
    )
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
    concernedProLoginInfo -> concernedProUser,
    notConcernedProLoginInfo -> notConcernedProUser
  ))

  def postReportResponse(reportResponse: ReportResponse) =  {
    Await.result(
      app.injector.instanceOf[ReportController].reportResponse(reportFixture.id.toString)
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest("POST", s"/api/reports/${reportFixture.id}/response")).withBody(Json.toJson(reportResponse))),
      Duration.Inf)
  }

  def resultStatusMustBe(status: Int) = {
    someResult must beSome and someResult.get.header.status === status
  }

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = Nil) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        Nil,
        subject,
        bodyHtml,
        attachments
      )
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.list, Duration.Inf).toList
    events.map(_.action) must contain(action)
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatusValue) = {
    report = Await.result(reportRepository.getReport(reportFixture.id), Duration.Inf).get
    report must reportStatusMatcher(status)
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"status doesn't match ${status} - ${report}")
  }

  def reportFileMustHaveBeenAttachedToReport() = {
    val reportFile = Await.result(reportRepository.getFile(reportResponseFile.id), Duration.Inf).get
    reportFile must beEqualTo(reportResponseFile.copy(reportId = Some(reportFixture.id)))
  }

}