package controllers.report

import controllers.routes
import models.UserRole.Admin
import models.report.Report
import models.report.ReportStatus
import models.report.review.ResponseConsumerReview
import models.report.review.ConsumerReviewApi
import models.report.review.ConsumerReviewApi.consumerReviewApiWrites
import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import play.mvc.Http.Status
import repositories.event.EventFilter
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import utils.AuthHelpers._

import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReviewOnReportWithoutResponse(implicit ee: ExecutionEnv) extends ReviewOnReportResponseSpec {
  override def is =
    s2"""
         Given a report without response                              ${step { reportId = reportWithoutResponse.id }}
         When post a review                                          ${step {
        someResult = Some(postReview(review))
      }}
         Then result status is forbidden                              ${resultStatusMustBe(Status.FORBIDDEN)}
    """
}

class FirstReviewOnReport(implicit ee: ExecutionEnv) extends ReviewOnReportResponseSpec {
  override def is =
    s2"""
         Given a report with a response                               ${step { reportId = reportWithResponse.id }}
         When post a review                                          ${step {
        someResult = Some(postReview(review))
      }}
         Then result status is OK                                     ${resultStatusMustBe(Status.OK)}
         And an event "REVIEW_ON_REPORT_RESPONSE" is created          ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_REVIEW_ON_RESPONSE
      )}
    """
}

class SecondReviewOnReport(implicit ee: ExecutionEnv) extends ReviewOnReportResponseSpec {
  override def is =
    s2"""
         Given a report with a review                                ${step { reportId = reportWithReview.id }}
         When post a review                                          ${step {
        someResult = Some(postReview(review))
      }}
         Then result status is OK                               ${resultStatusMustBe(Status.OK)}
    """
}

class GetReviewOnReport(implicit ee: ExecutionEnv) extends ReviewOnReportResponseSpec {
  override def is =
    s2"""
         Given a report with a review   When post a review then the response is found $e1"""

  def e1 = {
    val result = route(
      app,
      FakeRequest(GET, routes.ReportConsumerReviewController.getConsumerReview(reportWithExistingReview.id).toString)
        .withAuthCookie(adminUser.email, components.cookieAuthenticator)
    ).get

    status(result) must beEqualTo(OK)
    val responseConsumerReviewApi = Helpers.contentAsJson(result).as[ConsumerReviewApi]
    responseConsumerReviewApi.evaluation mustEqual consumerReview.evaluation
    responseConsumerReviewApi.details mustEqual consumerReview.details
  }
}

abstract class ReviewOnReportResponseSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  val adminUser = Fixtures.genAdminUser.sample.get

  val (app, components) = TestApp.buildApp(
  )

  lazy val userRepository                   = components.userRepository
  lazy val reportRepository                 = components.reportRepository
  lazy val eventRepository                  = components.eventRepository
  lazy val responseConsumerReviewRepository = components.responseConsumerReviewRepository
  lazy val companyRepository                = components.companyRepository

  val review = ConsumerReviewApi(ResponseEvaluation.Positive, Some("Response Details..."))

  val company = Fixtures.genCompany.sample.get

  val reportWithoutResponse = Fixtures.genReportForCompany(company).sample.get.copy(status = ReportStatus.Transmis)

  val reportWithResponse = Fixtures.genReportForCompany(company).sample.get.copy(status = ReportStatus.PromesseAction)
  val responseEvent =
    Fixtures.genEventForReport(reportWithResponse.id, EventType.PRO, ActionEvent.REPORT_PRO_RESPONSE).sample.get

  val reportWithReview = Fixtures.genReportForCompany(company).sample.get.copy(status = ReportStatus.PromesseAction)
  val reportWithExistingReview =
    Fixtures.genReportForCompany(company).sample.get.copy(status = ReportStatus.PromesseAction)
  val responseWithReviewEvent =
    Fixtures.genEventForReport(reportWithReview.id, EventType.PRO, ActionEvent.REPORT_PRO_RESPONSE).sample.get

  val consumerReview =
    ResponseConsumerReview(
      ResponseConsumerReviewId.generateId(),
      reportWithExistingReview.id,
      ResponseEvaluation.Positive,
      OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
      Some("Response Details...")
    )

  val consumerConflictReview =
    ResponseConsumerReview(
      ResponseConsumerReviewId.generateId(),
      reportWithReview.id,
      ResponseEvaluation.Positive,
      OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
      None
    )

  var reportId = UUID.randomUUID()

  var someResult: Option[Result] = None

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(adminUser)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(reportWithoutResponse)
        _ <- reportRepository.create(reportWithResponse)
        _ <- reportRepository.create(reportWithReview)
        _ <- responseConsumerReviewRepository.create(consumerConflictReview)
        _ <- reportRepository.create(reportWithExistingReview)
        _ <- responseConsumerReviewRepository.create(consumerReview)
        _ <- eventRepository.create(responseEvent)
        _ <- eventRepository.create(responseWithReviewEvent)
      } yield (),
      Duration.Inf
    )

  def postReview(reviewOnReportResponse: ConsumerReviewApi) =
    Await.result(
      components.reportConsumerReviewController
        .createConsumerReview(reportId)
        .apply(
          FakeRequest("POST", s"/api/reports/${reportId}/response/review")
            .withBody(Json.toJson(reviewOnReportResponse)(consumerReviewApiWrites(Admin)))
        ),
      Duration.Inf
    )

  def resultStatusMustBe(status: Int) =
    someResult.isDefined mustEqual true and someResult.get.header.status === status

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events =
      Await.result(eventRepository.getEvents(reportId, EventFilter(action = Some(action))), Duration.Inf).toList
    events.length must beEqualTo(1)
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatus) = {
    val report = Await.result(reportRepository.get(reportId), Duration.Inf).get
    report must reportStatusMatcher(status)
  }

  def reportStatusMatcher(status: ReportStatus): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"status doesn't match ${status} - ${report}")
  }
}
