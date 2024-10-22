package models

import models.report.ReportFilter
import models.report.ReportStatus
import models.report.ReportTag
import org.specs2.mutable.Specification
import utils.DateUtils

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.util.Success

class ReportFilterTest extends Specification {

  "ReportFilter" should {

    "fromQueryString should parse empty map" in {
      val emptyFilter = ReportFilter()
      ReportFilter.fromQueryString(Map.empty) shouldEqual Success(emptyFilter)
    }

    "fromQueryString should parse website http://www.domain.com as domain.com" in {
      val expectedResult = ReportFilter(websiteURL = Some("domain.com"))
      ReportFilter
        .fromQueryString(Map("websiteURL" -> Seq("http://www.domain.com")))
        .map(_.websiteURL) shouldEqual Success(expectedResult.websiteURL)
    }

    "fromQueryString should parse website domain.com as domain.com" in {
      val expectedResult = ReportFilter(websiteURL = Some("domain.com"))
      ReportFilter.fromQueryString(Map("websiteURL" -> Seq("domain.com"))).map(_.websiteURL) shouldEqual Success(
        expectedResult.websiteURL
      )
    }

    "fromQueryString should parse website domain as domain" in {
      val expectedResult = ReportFilter(websiteURL = Some("domain"))
      ReportFilter.fromQueryString(Map("websiteURL" -> Seq("domain"))).map(_.websiteURL) shouldEqual Success(
        expectedResult.websiteURL
      )
    }

    "fromQueryString should parse successfully" in {

      val websiteURL = Some("http://signal.conso.gouv.fr")
      val expectedReportFilter = ReportFilter(
        departments = Seq("75016", "78210"),
        email = Some("sc@signal.conso.gouv.fr"),
        websiteURL = Some("signal.conso.gouv.fr"),
        phone = Some("0100000000"),
        hasWebsite = Some(true),
        hasPhone = Some(true),
        hasForeignCountry = Some(true),
        siretSirenList = Seq("XXXXXXXXXXXXXX", "YYYYYYYYYYYYYY"),
        companyIds = Seq(UUID.randomUUID(), UUID.randomUUID()),
        companyName = Some("Company_Name"),
        companyCountries = Seq("FR", "EN"),
        start = Some(OffsetDateTime.of(LocalDateTime.of(2021, 10, 10, 19, 30, 40), ZoneOffset.of("+02:00"))),
        end = Some(OffsetDateTime.of(LocalDateTime.of(2021, 12, 10, 7, 0, 25), ZoneOffset.of("-05:00"))),
        category = Some("Categorie"),
        status = ReportStatus.statusVisibleByPro,
        details = Some("My Details"),
        hasCompany = Some(true),
        withTags = ReportTag.values,
        withoutTags = ReportTag.values,
        activityCodes = Seq("00.00Z")
      )

      val input = Map[String, Seq[String]](
        "departments"       -> expectedReportFilter.departments,
        "email"             -> expectedReportFilter.email.toSeq,
        "websiteURL"        -> websiteURL.toSeq,
        "phone"             -> expectedReportFilter.phone.toSeq,
        "hasWebsite"        -> expectedReportFilter.hasWebsite.toSeq.map(_.toString),
        "hasPhone"          -> expectedReportFilter.hasPhone.toSeq.map(_.toString),
        "hasForeignCountry" -> expectedReportFilter.hasPhone.toSeq.map(_.toString),
        "siretSirenList"    -> expectedReportFilter.siretSirenList,
        "companyName"       -> expectedReportFilter.companyName.toSeq,
        "companyCountries"  -> expectedReportFilter.companyCountries,
        "start"             -> expectedReportFilter.start.toSeq.map(_.format(DateUtils.TIME_FORMATTER)),
        "end"               -> expectedReportFilter.end.toSeq.map(_.format(DateUtils.TIME_FORMATTER)),
        "category"          -> expectedReportFilter.category.toSeq,
        "companyIds"        -> expectedReportFilter.companyIds.map(_.toString),
        "status"            -> expectedReportFilter.status.map(_.entryName),
        "details"           -> expectedReportFilter.details.toSeq,
        "employeeConsumer"  -> expectedReportFilter.employeeConsumer.toSeq.map(_.toString),
        "hasCompany"        -> expectedReportFilter.hasCompany.toSeq.map(_.toString),
        "withTags"          -> expectedReportFilter.withTags.toSeq.map(_.entryName),
        "withoutTags"       -> expectedReportFilter.withoutTags.toSeq.map(_.entryName),
        "activityCodes"     -> expectedReportFilter.activityCodes.toSeq
      )
      ReportFilter.fromQueryString(input) shouldEqual Success(expectedReportFilter)
    }

  }

}
