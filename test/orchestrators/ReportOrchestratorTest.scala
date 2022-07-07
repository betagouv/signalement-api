package orchestrators

import akka.Done
import org.specs2.mutable.Specification
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import controllers.error.AppError._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReportOrchestratorTest(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  override def afterAll(): Unit = {
    app.stop()
    ()
  }

  val (app, components) = TestApp.buildApp()

  "Create Report" should {

    val aDraftReport = Fixtures.genDraftReport.sample.get

    "fail when reporting public company" in {
      val draftReportOnPublicCompany = aDraftReport.copy(
        companyActivityCode = Some("84.10")
      )
      val res =
        components.reportOrchestrator.validateAndCreateReport(draftReportOnPublicCompany)
      res must throwA[CannotReportPublicAdministration.type].await

    }

    "succeed when reporting private company" in {
      val draftReportOnPrivateCompany = aDraftReport.copy(
        companyActivityCode = Some("90.10")
      )
      val res =
        Await.result(components.reportOrchestrator.validateCompany(draftReportOnPrivateCompany), Duration.Inf)

      res mustEqual Done

    }

  }
}
