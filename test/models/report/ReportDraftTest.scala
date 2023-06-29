package models.report

import io.scalaland.chimney.dsl.TransformerOps
import models.company.Address
import models.report.ReportStatus.NA
import models.report.ReportStatus.TraitementEnCours
import models.report.ReportTag.AbsenceDeMediateur
import models.report.ReportTag.Bloctel
import models.report.ReportTag.Ehpad
import models.report.ReportTag.Hygiene
import models.report.ReportTag.ReponseConso
import org.specs2.mutable.Specification
import utils.Country
import utils.Fixtures
import utils.SIRET
import utils.URL

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReportDraftTest extends Specification {

  "ReportDraftTest" should {

    "generateReport should return report with default value" in {

      val aDraftReport = Fixtures.genDraftReport.sample.get.copy(
        companyAddress = None,
        forwardToReponseConso = None,
        employeeConsumer = true,
        tags = List(ReportTag.LitigeContractuel),
        reponseconsoCode = None,
        ccrfCode = None,
        companyActivityCode = Some("40.7Z")
      )

      val reportId = UUID.randomUUID()
      val creationDate: OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val expirationDate = creationDate.plusDays(100)
      val res =
        aDraftReport.generateReport(
          maybeCompanyId = None,
          socialNetworkCompany = None,
          creationDate = creationDate,
          expirationDate = expirationDate,
          reportId = reportId
        )

      val expectedReport = aDraftReport
        .into[Report]
        .withFieldConst(_.forwardToReponseConso, false)
        .withFieldConst(_.ccrfCode, Nil)
        .withFieldComputed(_.websiteURL, r => WebsiteURL(r.websiteURL, r.websiteURL.flatMap(_.getHost)))
        .withFieldConst(_.reponseconsoCode, Nil)
        .withFieldConst(_.tags, Nil)
        .withFieldConst(_.companyAddress, Address())
        .withFieldConst(_.companyId, None)
        .withFieldConst(_.id, reportId)
        .withFieldConst(_.creationDate, creationDate)
        .withFieldConst(_.expirationDate, expirationDate)
        .withFieldConst(_.visibleToPro, false)
        .withFieldConst(_.status, ReportStatus.LanceurAlerte)
        .withFieldConst(_.influencer, None)
        .transform

      res shouldEqual expectedReport
    }

    val anInvalidDraftReport = Fixtures.genDraftReport.sample.get.copy(
      companySiret = None,
      websiteURL = None,
      companyAddress = None,
      tags = Nil,
      phone = None
    )

    "generateReport should be invalid" in {
      ReportDraft.isValid(anInvalidDraftReport) shouldEqual false
    }

    "generateReport should be valid when phone is defined" in {
      val aColdCallingDraftReport = anInvalidDraftReport.copy(
        phone = Some("0651445522")
      )
      ReportDraft.isValid(aColdCallingDraftReport) shouldEqual true
    }

    "generateReport should be valid when company siret is defined" in {
      val anIdentifiedCompanyDraftReport = anInvalidDraftReport.copy(
        companySiret = Some(SIRET("11111111451212"))
      )
      ReportDraft.isValid(anIdentifiedCompanyDraftReport) shouldEqual true
    }

    "generateReport should be valid when company siret is defined" in {
      val anInternetDraftReport = anInvalidDraftReport.copy(
        websiteURL = Some(URL("http://badcompany.com"))
      )
      ReportDraft.isValid(anInternetDraftReport) shouldEqual true
    }

    "generateReport should fail when reporting influencer without postal code" in {
      val anInfluencerDraftReport = anInvalidDraftReport.copy(
        tags = List(ReportTag.Influenceur)
      )
      ReportDraft.isValid(anInfluencerDraftReport) shouldEqual false
    }

    "generateReport should be valid when reporting influencer" in {
      val anInfluencerDraftReport = anInvalidDraftReport.copy(
        tags = List(ReportTag.Influenceur),
        companyAddress = Some(Address(postalCode = Some("75000")))
      )
      ReportDraft.isValid(anInfluencerDraftReport) shouldEqual true
    }

    "generateReport should be valid when reporting foreign country" in {
      val aForeignCountryDraftReport = anInvalidDraftReport.copy(
        companyAddress = Some(Address(country = Some(Country.Inde)))
      )
      ReportDraft.isValid(aForeignCountryDraftReport) shouldEqual true
    }

    "generateReport should be valid when reporting with postal code" in {
      val aPostalCodeDraftReport = anInvalidDraftReport.copy(
        companyAddress = Some(Address(postalCode = Some("888888")))
      )
      ReportDraft.isValid(aPostalCodeDraftReport) shouldEqual true
    }

    "generateReport should pick the correct status" in {

      val typicalDraftReport = Fixtures.genDraftReport.sample.get.copy(
        tags = List(Hygiene, AbsenceDeMediateur, Ehpad),
        employeeConsumer = false,
        companySiret = Some(SIRET("11111111111111"))
      )

      def generateReportFromDraft(draft: ReportDraft) = {
        val creationDate: OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val expirationDate = creationDate.plusDays(100)
        draft.generateReport(
          maybeCompanyId = None,
          socialNetworkCompany = None,
          creationDate = creationDate,
          expirationDate = expirationDate,
          reportId = UUID.randomUUID()
        )
      }

      s"initialStatus should be TraitementEnCours when company is identified, and report tags are nothing special" in {
        val report = generateReportFromDraft(typicalDraftReport)
        report.status shouldEqual TraitementEnCours
      }

      s"initialStatus should be NA when company has not been identified" in {
        val report = generateReportFromDraft(typicalDraftReport.copy(companySiret = None))
        report.status shouldEqual NA
      }

      s"initialStatus should be LanceurAlerte if the draft had employeeCustomer set to true" in {
        val report = generateReportFromDraft(
          typicalDraftReport.copy(
            employeeConsumer = true
          )
        )
        report.status shouldEqual ReportStatus.LanceurAlerte
      }

      s"initialStatus should be NA when there is tag ReponseConso" in {
        val report = generateReportFromDraft(
          typicalDraftReport.copy(
            tags = List(Hygiene, AbsenceDeMediateur, Ehpad, ReponseConso)
          )
        )
        report.status shouldEqual NA
      }

      s"initialStatus should be NA when there is tag BlocTel" in {
        val report = generateReportFromDraft(
          typicalDraftReport.copy(
            tags = List(Hygiene, AbsenceDeMediateur, Ehpad, Bloctel)
          )
        )
        report.status shouldEqual NA
      }

    }

  }
}
