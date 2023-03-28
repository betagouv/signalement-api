package repositories

import models.report.ReportFilter
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable
import org.specs2.specification.BeforeAfterAll
import utils.Fixtures
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReportRepositorySpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with FutureMatchers
    with BeforeAfterAll {

  val (app, components) = TestApp.buildApp()

  val company = Fixtures.genCompany.sample.get
  val anonymousReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      contactAgreement = false,
      consumerReferenceNumber = Some("anonymousReference"),
      firstName = "anonymousFirstName",
      lastName = "anonymousLastName"
    )
  val report = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      contactAgreement = true,
      consumerReferenceNumber = Some("reference"),
      firstName = "firstName",
      lastName = "lastName"
    )

  override def beforeAll(): Unit = {
    Await.result(components.companyRepository.create(company), Duration.Inf)
    Await.result(components.reportRepository.create(report), Duration.Inf)
    Await.result(components.reportRepository.create(anonymousReport), Duration.Inf)

    ()
  }

  override def afterAll(): Unit = {
    Await.result(components.reportRepository.delete(report.id), Duration.Inf)
    Await.result(components.reportRepository.delete(anonymousReport.id), Duration.Inf)
    Await.result(components.companyRepository.delete(company.id), Duration.Inf)

    ()
  }

  "ReportRepository" should {
    "getReports" should {
      "not fetch anonymous users" in {
        for {
          a <- components.reportRepository.getReports(ReportFilter(fullText = Some("anonymousFirstName")))
          b <- components.reportRepository.getReports(ReportFilter(fullText = Some("anonymousReference")))
        } yield (a.entities must beEmpty) && (b.entities must beEmpty)
      }

      "fetch users" in {
        for {
          a <- components.reportRepository.getReports(ReportFilter(fullText = Some("firstName")))
          b <- components.reportRepository.getReports(ReportFilter(fullText = Some("reference")))
        } yield (a.entities must haveLength(1)) && (b.entities must haveLength(1))
      }

      "be case insensitive" in {
        for {
          a <- components.reportRepository.getReports(ReportFilter(fullText = Some("LASTNAME")))
          b <- components.reportRepository.getReports(ReportFilter(fullText = Some("REFERENCE")))
        } yield (a.entities must haveLength(1)) && (b.entities must haveLength(1))
      }
    }
  }

}
