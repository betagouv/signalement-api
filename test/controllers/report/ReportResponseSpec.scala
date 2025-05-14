package controllers.report

import models.User
import models.company.AccessLevel
import models.event.Event
import models.report._
import models.report.reportfile.ReportFileId
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.i18n.Lang
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.json.Json
import play.api.libs.mailer.Attachment
import play.api.mvc.Result
import play.api.test._
import play.mvc.Http.Status
import services.emails.MailRetriesService.EmailRequest
import utils.Constants.ActionEvent
import utils.Constants.ActionEvent.ActionEventValue
import utils._
import utils.AuthHelpers._

import java.time.temporal.ChronoUnit
import java.net.URI
import java.time.OffsetDateTime
import java.util.Locale
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReportResponseByUnauthenticatedUser(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step { someUser = None }}
         When post a response                                         ${step {
        someResult = Some(postReportResponse(reportResponseAccepted))
      }}
         Then result status is not authorized                         ${resultStatusMustBe(Status.UNAUTHORIZED)}
    """
}

class ReportResponseByNotConcernedProUser(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step {
        someUser = Some(notConcernedProUser)
      }}
         When post a response                                                   ${step {
        someResult = Some(postReportResponse(reportResponseAccepted))
      }}
         Then result status is not found                                        ${resultStatusMustBe(Status.NOT_FOUND)}
    """
}

class ReportResponseProAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  implicit val messagesProvider: MessagesProvider =
    MessagesImpl(Lang(report.lang.getOrElse(Locale.FRENCH)), messagesApi)

  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step {
        someUser = Some(concernedProUser)
      }}
        When post a response with type "ACCEPTED"                                ${step {
        someResult = Some(postReportResponse(reportResponseAccepted))
      }}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_PRO_RESPONSE
      )}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
      )}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
      )}
        And the response files are attached to the report                        ${reportFileMustHaveBeenAttachedToReport()}
        And the report reportStatusList is updated to "ReportStatus.PromesseAction"          ${reportMustHaveBeenUpdatedWithStatus(
        ReportStatus.PromesseAction
      )}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(
        reportFixture.email,
        "L'entreprise a répondu à votre signalement, donnez nous votre avis sur sa réponse",
        views.html.mails.consumer
          .reportToConsumerAcknowledgmentPro(
            report,
            Some(company),
            reportResponseAccepted.toExisting,
            isReassignable = false,
            frontRoute.website.reportReview(report.id.toString)
          )
          .toString,
        attachementService.ConsumerProResponseNotificationAttachement(Locale.FRENCH)
      )}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(
        concernedProUser.email,
        "Votre réponse au signalement",
        views.html.mails.professional
          .reportAcknowledgmentPro(reportResponseAccepted.toExisting, concernedProUser)
          .toString
      )}
    """
}

class ReportResponseProRejectedAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  implicit val messagesProvider: MessagesProvider =
    MessagesImpl(Lang(report.lang.getOrElse(Locale.FRENCH)), messagesApi)

  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step {
        someUser = Some(concernedProUser)
      }}
        When post a response with type "REJECTED"                                ${step {
        someResult = Some(postReportResponse(reportResponseRejected))
      }}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_PRO_RESPONSE
      )}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
      )}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
      )}
        And the report reportStatusList is updated to "ReportStatus.Infonde"      ${reportMustHaveBeenUpdatedWithStatus(
        ReportStatus.Infonde
      )}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(
        reportFixture.email,
        "L'entreprise a répondu à votre signalement, donnez nous votre avis sur sa réponse",
        views.html.mails.consumer
          .reportToConsumerAcknowledgmentPro(
            report,
            Some(company),
            reportResponseRejected.toExisting,
            isReassignable = false,
            frontRoute.website.reportReview(report.id.toString)
          )
          .toString,
        attachementService.ConsumerProResponseNotificationAttachement(Locale.FRENCH)
      )}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(
        concernedProUser.email,
        "Votre réponse au signalement",
        views.html.mails.professional
          .reportAcknowledgmentPro(reportResponseRejected.toExisting, concernedProUser)
          .toString
      )}
    """
}

class ReportResponseProNotConcernedAnswer(implicit ee: ExecutionEnv) extends ReportResponseSpec {
  implicit val messagesProvider: MessagesProvider =
    MessagesImpl(Lang(report.lang.getOrElse(Locale.FRENCH)), messagesApi)

  override def is =
    s2"""
        Given an authenticated pro user which is concerned by the report         ${step {
        someUser = Some(concernedProUser)
      }}
        When post a response with type "NOT_CONCERNED"                           ${step {
        someResult = Some(postReportResponse(reportResponseNotConcerned))
      }}
        Then an event "REPORT_PRO_RESPONSE" is created                           ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_PRO_RESPONSE
      )}
        And an event "EMAIL_CONSUMER_REPORT_RESPONSE" is created                 ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
      )}
        And an event "EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT" is created              ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
      )}
        And the report reportStatusList is updated to "MAL_ATTRIBUE"             ${reportMustHaveBeenUpdatedWithStatus(
        ReportStatus.MalAttribue
      )}
        And an acknowledgment email is sent to the consumer                      ${mailMustHaveBeenSent(
        reportFixture.email,
        "L'entreprise a répondu à votre signalement, donnez nous votre avis sur sa réponse",
        views.html.mails.consumer
          .reportToConsumerAcknowledgmentPro(
            report,
            Some(company),
            reportResponseNotConcerned.toExisting,
            isReassignable = true,
            frontRoute.website.reportReview(report.id.toString)
          )
          .toString,
        attachementService.ConsumerProResponseNotificationAttachement(Locale.FRENCH)
      )}
        And an acknowledgment email is sent to the professional                  ${mailMustHaveBeenSent(
        concernedProUser.email,
        "Votre réponse au signalement",
        views.html.mails.professional
          .reportAcknowledgmentPro(reportResponseNotConcerned.toExisting, concernedProUser)
          .toString
      )}
    """
}

abstract class ReportResponseSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val reportRepository                      = components.reportRepository
  lazy val reportFileRepository                  = components.reportFileRepository
  lazy val userRepository                        = components.userRepository
  lazy val eventRepository                       = components.eventRepository
  lazy val companyRepository                     = components.companyRepository
  lazy val companyAccessRepository               = components.companyAccessRepository
  lazy val AccessTokenRepositoryInterface        = components.accessTokenRepository
  lazy val mailRetriesService                    = components.mailRetriesService
  lazy val attachementService                    = components.attachmentService
  implicit lazy val frontRoute: utils.FrontRoute = components.frontRoute
  lazy val messagesApi                           = components.messagesApi

  val contactEmail = EmailAddress("contact@signal.conso.gouv.fr")

  val siretForConcernedPro    = Fixtures.genSiret().sample.get
  val siretForNotConcernedPro = Fixtures.genSiret().sample.get

  val company = Fixtures.genCompany.sample.get.copy(siret = siretForConcernedPro, isHeadOffice = false)

  val reportFixture = Fixtures.genReportForCompany(company).sample.get.copy(status = ReportStatus.Transmis)

  var reviewUrl = new URI("")
  var report    = reportFixture

  val concernedProUser = Fixtures.genProUser.sample.get

  val notConcernedProUser = Fixtures.genProUser.sample.get

  var someUser: Option[User]     = None
  var someResult: Option[Result] = None

  val reportResponseFile = ReportFile(
    ReportFileId.generateId(),
    None,
    OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    "fichier.jpg",
    "123_fichier.jpg",
    ReportFileOrigin.Professional,
    None
  )

  val reportResponseAccepted = IncomingReportResponse(
    ReportResponseType.ACCEPTED,
    "details for consumer",
    Some("details for dgccrf"),
    List(reportResponseFile.id),
    responseDetails = Some(ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR)
  )
  val reportResponseRejected =
    IncomingReportResponse(
      ReportResponseType.REJECTED,
      "details for consumer",
      Some("details for dgccrf"),
      List.empty,
      responseDetails = Some(ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR)
    )
  val reportResponseNotConcerned =
    IncomingReportResponse(
      ReportResponseType.NOT_CONCERNED,
      "details for consumer",
      Some("details for dgccrf"),
      List.empty,
      responseDetails = Some(ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR)
    )

  override def setupData() = {
    reviewUrl = new URI(
      configLoader.dashboardURL.toString + s"/suivi-des-signalements/${reportFixture.id}/avis"
    )
    Await.result(
      for {
        _ <- userRepository.create(concernedProUser)
        _ <- userRepository.create(notConcernedProUser)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- companyAccessRepository.createAccess(company.id, concernedProUser.id, AccessLevel.ADMIN)
        _ <- reportRepository.create(reportFixture)
        _ <- reportFileRepository.create(reportResponseFile)
      } yield (),
      Duration.Inf
    )
  }

  val (app, components) = TestApp.buildApp(
  )

  def postReportResponse(reportResponse: IncomingReportResponse) =
    Await.result(
      components.reportController
        .createReportResponse(reportFixture.id)
        .apply(
          someUser
            .map(user => FakeRequest().withAuthCookie(user.email, components.cookieAuthenticator))
            .getOrElse(FakeRequest("POST", s"/api/reports/${reportFixture.id}/response"))
            .withBody(Json.toJson(reportResponse))
        ),
      Duration.Inf
    )

  def resultStatusMustBe(status: Int) =
    someResult.isDefined mustEqual true and someResult.get.header.status === status

  def mailMustHaveBeenSent(
      recipient: EmailAddress,
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment] = attachementService.defaultAttachments
  ) =
    there was one(mailRetriesService).sendEmailWithRetries(
      argThat((emailRequest: EmailRequest) =>
        emailRequest.recipients.sortBy(_.value).toList == List(recipient) &&
          emailRequest.subject === subject && emailRequest.bodyHtml === bodyHtml && emailRequest.attachments == attachments
      )
    )

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.list(), Duration.Inf).toList
    events.map(_.action) must contain(action)
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatus) = {
    report = Await.result(reportRepository.get(reportFixture.id), Duration.Inf).get
    report must reportStatusMatcher(status)
  }

  def reportStatusMatcher(status: ReportStatus): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"status doesn't match ${status} - ${report}")
  }

  def reportFileMustHaveBeenAttachedToReport() = {
    val reportFile = Await.result(reportFileRepository.get(reportResponseFile.id), Duration.Inf).get
    reportFile must beEqualTo(reportResponseFile.copy(reportId = Some(reportFixture.id)))
  }

}
