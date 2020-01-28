package controllers.report

import java.util.UUID

import controllers.ReportController
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test._
import play.mvc.Http.Status
import repositories._
import utils.Constants.{ActionEvent, EventType}
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus.{PROMESSE_ACTION, ReportStatusValue, SIGNALEMENT_TRANSMIS}
import utils.{AppSpec, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class AdviceOnReportWithoutResponse(implicit ee: ExecutionEnv) extends AdviceOnReportResponseSpec  {
  override def is =
    s2"""
         Given a report without response                              ${step(reportId = reportWithoutResponse.id)}
         When post an advice                                          ${step(someResult = Some(postAdvice(adviceOnReportResponse)))}
         Then result status is forbidden                              ${resultStatusMustBe(Status.FORBIDDEN)}
    """
}

class FirstAdviceOnReport(implicit ee: ExecutionEnv) extends AdviceOnReportResponseSpec  {
  override def is =
    s2"""
         Given a report with a response                               ${step(reportId = reportWithResponse.id)}
         When post an advice                                          ${step(someResult = Some(postAdvice(adviceOnReportResponse)))}
         Then result status is OK                                     ${resultStatusMustBe(Status.OK)}
         And an event "ADVICE_ON_REPORT_RESPONSE" is created          ${eventMustHaveBeenCreatedWithAction(ActionEvent.ADVICE_ON_REPORT_RESPONSE)}
    """
}

class SecondAdviceOnReport(implicit ee: ExecutionEnv) extends AdviceOnReportResponseSpec  {
  override def is =
    s2"""
         Given a report with an advice                                ${step(reportId = reportWithAdvice.id)}
         When post an advice                                          ${step(someResult = Some(postAdvice(adviceOnReportResponse)))}
         Then result status is CONFLICT                               ${resultStatusMustBe(Status.CONFLICT)}
    """
}

abstract class AdviceOnReportResponseSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val reportRepository = app.injector.instanceOf[ReportRepository]
  lazy val eventRepository = app.injector.instanceOf[EventRepository]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]

  val company = Fixtures.genCompany.sample.get

  val reportWithoutResponse = Fixtures.genReportForCompany(company).sample.get.copy(status = SIGNALEMENT_TRANSMIS)

  val reportWithResponse = Fixtures.genReportForCompany(company).sample.get.copy(status = PROMESSE_ACTION)
  val responseEvent = Fixtures.genEventForReport(reportWithResponse.id, EventType.PRO, ActionEvent.REPONSE_PRO_SIGNALEMENT).sample.get

  val reportWithAdvice = Fixtures.genReportForCompany(company).sample.get.copy(status = PROMESSE_ACTION)
  val responseWithAdviceEvent = Fixtures.genEventForReport(reportWithAdvice.id, EventType.PRO, ActionEvent.REPONSE_PRO_SIGNALEMENT).sample.get
  val adviceEvent = Fixtures.genEventForReport(reportWithAdvice.id, EventType.PRO, ActionEvent.ADVICE_ON_REPORT_RESPONSE).sample.get

  val adviceOnReportResponse = Fixtures.genAdviceOnReportResponse.sample.get

  var reportId = UUID.randomUUID()

  var someResult: Option[Result] = None

  override def setupData = {
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- reportRepository.create(reportWithoutResponse)
        _ <- reportRepository.create(reportWithResponse)
        _ <- reportRepository.create(reportWithAdvice)
        _ <- eventRepository.createEvent(responseEvent)
        _ <- eventRepository.createEvent(responseWithAdviceEvent)
        _ <- eventRepository.createEvent(adviceEvent)
      } yield Unit,
      Duration.Inf
    )
  }

  def postAdvice(adviceOnReportResponse: AdviceOnReportResponse) =  {
    Await.result(
      app.injector.instanceOf[ReportController].adviceOnReportResponse(reportId.toString)
        .apply(FakeRequest("POST", s"/api/reports/${reportId}/response/advice").withBody(Json.toJson(adviceOnReportResponse))),
      Duration.Inf)
  }

  def resultStatusMustBe(status: Int) = {
    someResult must beSome and someResult.get.header.status === status
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    val events = Await.result(eventRepository.getEvents(reportId, EventFilter(action = Some(action))), Duration.Inf).toList
    events.length must beEqualTo(1)
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatusValue) = {
    val report = Await.result(reportRepository.getReport(reportId), Duration.Inf).get
    report must reportStatusMatcher(status)
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"status doesn't match ${status} - ${report}")
  }
}