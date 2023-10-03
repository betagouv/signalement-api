package repositories

import models.report.ReportFilter
import models.report.ReportTag
import models.report.ReportsCountBySubcategoriesFilter
import models.report.reportmetadata.Os
import models.report.reportmetadata.ReportMetadata
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.TraversableMatchers
import org.specs2.mutable
import org.specs2.specification.BeforeAfterAll
import utils.Fixtures
import utils.TestApp

import java.util.Locale
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReportRepositorySpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with FutureMatchers
    with TraversableMatchers
    with BeforeAfterAll {

  val (app, components) = TestApp.buildApp()

  val company = Fixtures.genCompany.sample.get
  val anonymousReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatMagasin",
      subcategories = List("a", "b", "c"),
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
      category = "AchatMagasin",
      subcategories = List("a", "b", "c"),
      contactAgreement = true,
      consumerReferenceNumber = Some("reference"),
      firstName = "firstName",
      lastName = "lastName"
    )

  val report2 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatInternet",
      subcategories = List("a", "b", "c")
    )

  val report3 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatInternet",
      subcategories = List("a", "b", "d")
    )

  val report4 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatMagasin",
      subcategories = List("a", "b", "c"),
      tags = List(ReportTag.ReponseConso),
      phone = Some("0102030405")
    )

  val englishReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(lang = Some(Locale.ENGLISH))

  override def beforeAll(): Unit = {
    Await.result(components.companyRepository.create(company), Duration.Inf)
    Await.result(components.reportRepository.create(report), Duration.Inf)
    Await.result(components.reportRepository.create(anonymousReport), Duration.Inf)
    Await.result(components.reportRepository.create(report2), Duration.Inf)
    Await.result(components.reportRepository.create(report3), Duration.Inf)
    Await.result(components.reportRepository.create(report4), Duration.Inf)
    Await.result(components.reportRepository.create(englishReport), Duration.Inf)
    Await.result(
      components.reportMetadataRepository.create(ReportMetadata(report2.id, false, Some(Os.Ios))),
      Duration.Inf
    )

    ()
  }

  override def afterAll(): Unit = {
    Await.result(components.reportRepository.delete(report.id), Duration.Inf)
    Await.result(components.reportRepository.delete(anonymousReport.id), Duration.Inf)
    Await.result(components.reportRepository.delete(report2.id), Duration.Inf)
    Await.result(components.reportRepository.delete(report3.id), Duration.Inf)
    Await.result(components.reportRepository.delete(report4.id), Duration.Inf)
    Await.result(components.reportRepository.delete(englishReport.id), Duration.Inf)
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

    "reportsCountBySubcategories" should {
      "fetch french jobs" in {
        for {
          res <- components.reportRepository.reportsCountBySubcategories(
            ReportsCountBySubcategoriesFilter(),
            Locale.FRENCH
          )
        } yield res should contain(
          ("AchatInternet", List("a", "b", "c"), 1, 0),
          ("AchatInternet", List("a", "b", "d"), 1, 0),
          ("AchatMagasin", List("a", "b", "c"), 3, 1)
        )
      }
    }

    "getPhoneReports" in {
      for {
        res <- components.reportRepository.getPhoneReports(None, None)
      } yield res should containTheSameElementsAs(List(report4))
    }

    "deleteReport" should {
      "work when metadata exist and delete cascade" in {
        components.reportRepository.delete(report2.id).map(_ mustEqual 1)
      }
    }
  }

}
