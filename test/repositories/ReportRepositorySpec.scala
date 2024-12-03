package repositories

import models.report.ReportFilter
import models.report.ReportStatus
import models.report.ReportTag
import models.report.ReportsCountBySubcategoriesFilter
import models.report.reportmetadata.Os
import models.report.reportmetadata.ReportMetadata
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.TraversableMatchers
import org.specs2.mutable
import org.specs2.specification.BeforeAfterAll
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.util.Locale
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ReportRepositorySpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with AppSpec
    with FutureMatchers
    with TraversableMatchers
    with BeforeAfterAll {

  val (app, components) = TestApp.buildApp()

  val userAdmin  = Fixtures.genAdminUser.sample.get
  val userDgccrf = Fixtures.genDgccrfUser.sample.get
  val userDgal   = Fixtures.genDgalUser.sample.get
  val userPro    = Fixtures.genProUser.sample.get
  val company    = Fixtures.genCompany.sample.get
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
      lastName = "anonymousLastName",
      status = ReportStatus.NonConsulte
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
      lastName = "lastName",
      status = ReportStatus.Transmis
    )

  val report2 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatInternet",
      subcategories = List("a", "b", "c"),
      visibleToPro = false
    )

  val report3 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatInternet",
      subcategories = List("a", "b", "d"),
      visibleToPro = false
    )

  val report4 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatMagasin",
      subcategories = List("a", "b", "c"),
      tags = List(ReportTag.ReponseConso),
      phone = Some("0102030405"),
      status = ReportStatus.TraitementEnCours
    )

  val report5 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "IntoxicationAlimentaire",
      subcategories = List("a"),
      visibleToPro = false,
      status = ReportStatus.TraitementEnCours
    )

  val report6 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "CafeRestaurant",
      subcategories = List("a"),
      tags = List(ReportTag.ProduitAlimentaire),
      status = ReportStatus.NA
    )
  val report7 = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "AchatMagasin",
      subcategories = List("z"),
      contactAgreement = false,
      consumerReferenceNumber = Some("XB780"),
      firstName = "Jean",
      lastName = "Lino",
      visibleToPro = false
    )

  val englishReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      category = "TravauxRenovations",
      lang = Some(Locale.ENGLISH),
      status = ReportStatus.Infonde
    )

  override def setupData(): Unit = {
    Await.result(components.companyRepository.create(company), Duration.Inf)
    Await.result(components.reportRepository.create(report), Duration.Inf)
    Await.result(components.reportRepository.create(anonymousReport), Duration.Inf)
    Await.result(components.reportRepository.create(report2), Duration.Inf)
    Await.result(components.reportRepository.create(report3), Duration.Inf)
    Await.result(components.reportRepository.create(report4), Duration.Inf)
    Await.result(components.reportRepository.create(report5), Duration.Inf)
    Await.result(components.reportRepository.create(report6), Duration.Inf)
    Await.result(components.reportRepository.create(report7), Duration.Inf)
    Await.result(components.reportRepository.create(englishReport), Duration.Inf)
    Await.result(
      components.reportMetadataRepository.create(ReportMetadata(report2.id, false, Some(Os.Ios), None)),
      Duration.Inf
    )

    ()
  }

  "ReportRepository" should {
    "getReports" should {
      "not fetch anonymous users" in {
        for {
          a <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("anonymousFirstName")),
            None,
            None,
            None,
            None
          )
          b <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("anonymousReference")),
            None,
            None,
            None,
            None
          )
        } yield (a.entities must beEmpty) && (b.entities must beEmpty)
      }

      "fetch users" in {
        for {
          a <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("firstName")),
            None,
            None,
            None,
            None
          )
          b <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("reference")),
            None,
            None,
            None,
            None
          )
        } yield (a.entities must haveLength(1)) && (b.entities must haveLength(1))
      }

      "be case insensitive" in {
        for {
          a <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("LASTNAME")),
            None,
            None,
            None,
            None
          )
          b <- components.reportRepository.getReports(
            None,
            ReportFilter(fullText = Some("REFERENCE")),
            None,
            None,
            None,
            None
          )
        } yield (a.entities must haveLength(1)) && (b.entities must haveLength(1))
      }

      "fetch for anonymous users when user is admin" in {
        for {
          a <- components.reportRepository.getReports(
            None,
            ReportFilter(details = Some("anonymousFirstName")),
            None,
            None,
            None,
            None
          )
          b <- components.reportRepository.getReports(
            None,
            ReportFilter(details = Some("anonymousReference")),
            None,
            None,
            None,
            None
          )
        } yield (a.entities must haveLength(1)) && (b.entities must haveLength(1))
      }

      "return all reports for an admin user" in {
        components.reportRepository
          .getReports(Some(userAdmin), ReportFilter(), None, None, None, None)
          .map(result => result.entities must haveLength(9))
      }

      "return all reports for a DGCCRF user" in {
        components.reportRepository
          .getReports(Some(userDgccrf), ReportFilter(), None, None, None, None)
          .map(result => result.entities must haveLength(9))
      }

      "return only visible to DGAL for a DGAL user" in {
        components.reportRepository
          .getReports(Some(userDgal), ReportFilter(), None, None, None, None)
          .map(result => result.entities must haveLength(2))
      }

      "return all reports for a pro user" in {
        components.reportRepository
          .getReports(Some(userPro), ReportFilter(), None, None, None, None)
          .map(result => result.entities must haveLength(4))
      }
    }

    "reportsCountBySubcategories" should {
      "fetch french jobs" in {
        for {
          res <- components.reportRepository.reportsCountBySubcategories(
            userAdmin,
            ReportsCountBySubcategoriesFilter(),
            Locale.FRENCH
          )
        } yield res should contain(
          ("AchatInternet", List("a", "b", "c"), 1, 0),
          ("AchatInternet", List("a", "b", "d"), 1, 0),
          ("AchatMagasin", List("a", "b", "c"), 3, 1),
          ("IntoxicationAlimentaire", List("a"), 1, 0)
        )
      }

      "filter results when user is DGAL" in {
        for {
          res <- components.reportRepository.reportsCountBySubcategories(
            userDgal,
            ReportsCountBySubcategoriesFilter(),
            Locale.FRENCH
          )
        } yield {
          res should not(
            contain(
              ("AchatInternet", List("a", "b", "c"), 1, 0),
              ("AchatInternet", List("a", "b", "d"), 1, 0),
              ("AchatMagasin", List("a", "b", "c"), 3, 1)
            )
          )
          res should contain(("IntoxicationAlimentaire", List("a"), 1, 0))
        }
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
