package models

import models.UserRole.Admin
import models.UserRole.DGCCRF
import models.UserRole.Professionnel
import org.specs2.mutable.Specification
import utils.Constants.Tags
import utils.DateUtils

import java.time.LocalDate
import java.util.UUID
import scala.util.Success

class ReportFilterTest extends Specification {

  "ReportFilter" should {

    "fromQueryString should parse empty map for Admin user" in {
      val emptyFilter = ReportFilter(status = ReportStatus.values)
      ReportFilter.fromQueryString(Map.empty, Admin) shouldEqual Success(emptyFilter)
    }

    "fromQueryString should parse empty map for DGCCRF user" in {
      val emptyFilter = ReportFilter(status = ReportStatus.values)
      ReportFilter.fromQueryString(Map.empty, DGCCRF) shouldEqual Success(emptyFilter)
    }

    "fromQueryString should parse empty map for Pro user" in {
      val emptyFilter = ReportFilter(employeeConsumer = Some(false), status = ReportStatus.statusVisibleByPro)
      ReportFilter.fromQueryString(Map.empty, Professionnel) shouldEqual Success(emptyFilter)
    }

    "fromQueryString should parse successfully" in {

      val expectedReportFilter = ReportFilter(
        departments = Seq("75016", "78210"),
        email = Some("sc@signal.conso.gouv.fr"),
        websiteURL = Some("http://signal.conso.gouv.fr"),
        phone = Some("0100000000"),
        hasWebsite = Some(true),
        hasPhone = Some(true),
        hasForeignCountry = Some(true),
        siretSirenList = Seq("XXXXXXXXXXXXXX", "YYYYYYYYYYYYYY"),
        companyIds = Seq(UUID.randomUUID(), UUID.randomUUID()),
        companyName = Some("Company_Name"),
        companyCountries = Seq("FR", "EN"),
        start = Some(LocalDate.of(2021, 10, 10)),
        end = Some(LocalDate.of(2021, 12, 10)),
        category = Some("Categorie"),
        status = ReportStatus.statusVisibleByPro,
        details = Some("My Details"),
        employeeConsumer = Some(false),
        hasCompany = Some(true),
        tags = Seq(Tags.ReponseConso),
        activityCodes = Seq("00.00Z")
      )
      val input = Map[String, Seq[String]](
        "departments" -> expectedReportFilter.departments,
        "email" -> expectedReportFilter.email.toSeq,
        "websiteURL" -> expectedReportFilter.websiteURL.toSeq,
        "phone" -> expectedReportFilter.phone.toSeq,
        "hasWebsite" -> expectedReportFilter.hasWebsite.toSeq.map(_.toString),
        "hasPhone" -> expectedReportFilter.hasPhone.toSeq.map(_.toString),
        "hasForeignCountry" -> expectedReportFilter.hasPhone.toSeq.map(_.toString),
        "siretSirenList" -> expectedReportFilter.siretSirenList,
        "companyName" -> expectedReportFilter.companyName.toSeq,
        "companyCountries" -> expectedReportFilter.companyCountries,
        "start" -> expectedReportFilter.start.toSeq.map(_.format(DateUtils.FORMATTER)),
        "end" -> expectedReportFilter.end.toSeq.map(_.format(DateUtils.FORMATTER)),
        "category" -> expectedReportFilter.category.toSeq,
        "companyIds" -> expectedReportFilter.companyIds.map(_.toString),
        "status" -> expectedReportFilter.status.map(_.entryName),
        "details" -> expectedReportFilter.details.toSeq,
        "employeeConsumer" -> expectedReportFilter.employeeConsumer.toSeq.map(_.toString),
        "hasCompany" -> expectedReportFilter.hasCompany.toSeq.map(_.toString),
        "tags" -> expectedReportFilter.tags.toSeq,
        "activityCodes" -> expectedReportFilter.activityCodes.toSeq
      )

      ReportFilter.fromQueryString(input, Professionnel) shouldEqual Success(expectedReportFilter)
    }

  }

}
