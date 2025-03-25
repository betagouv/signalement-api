package models.report

import io.scalaland.chimney.dsl.TransformationOps
import models.report.review.ResponseEvaluation
import utils.QueryStringMapper
import utils.URL

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

case class ReportFilterApi(
    departments: Seq[String] = Seq.empty,
    email: Option[String] = None,
    consumerPhone: Option[String] = None,
    hasConsumerPhone: Option[Boolean] = None,
    websiteURL: Option[String] = None,
    phone: Option[String] = None,
    siretSirenList: Seq[String] = Seq.empty,
    companyIds: Seq[UUID] = Seq.empty,
    companyName: Option[String] = None,
    companyCountries: Seq[String] = Seq.empty,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None,
    category: Option[String] = None,
    subcategories: Seq[String] = Seq.empty,
    status: Seq[ReportStatus] = Seq.empty,
    details: Option[String] = None,
    employeeConsumer: Option[Boolean] = None,
    contactAgreement: Option[Boolean] = None,
    hasForeignCountry: Option[Boolean] = None,
    hasWebsite: Option[Boolean] = None,
    hasPhone: Option[Boolean] = None,
    hasCompany: Option[Boolean] = None,
    hasAttachment: Option[Boolean] = None,
    withTags: Seq[ReportTag] = Seq.empty,
    withoutTags: Seq[ReportTag] = Seq.empty,
    activityCodes: Seq[String] = Seq.empty,
    hasResponseEvaluation: Option[Boolean] = None,
    responseEvaluation: Seq[ResponseEvaluation] = Seq.empty,
    hasEngagementEvaluation: Option[Boolean] = None,
    engagementEvaluation: Seq[ResponseEvaluation] = Seq.empty,
    visibleToPro: Option[Boolean] = None,
    isForeign: Option[Boolean] = None,
    hasBarcode: Option[Boolean] = None,
    isBookmarked: Option[Boolean] = None
)

object ReportFilterApi {
  private[models] def hostFromWebsiteFilter(websiteFilter: Option[String]) =
    websiteFilter.flatMap(website => URL(website).getHost.orElse(websiteFilter))

  def fromQueryString(q: Map[String, Seq[String]]): Try[ReportFilterApi] = Try {
    val mapper = new QueryStringMapper(q)
    ReportFilterApi(
      departments = mapper.seq("departments"),
      email = mapper.string("email", trimmed = true),
      consumerPhone = mapper.phoneNumber("consumerPhone"),
      hasConsumerPhone = mapper.boolean("hasConsumerPhone"),
      websiteURL = hostFromWebsiteFilter(mapper.string("websiteURL", trimmed = true)),
      phone = mapper.phoneNumber("phone"),
      siretSirenList = mapper.seq("siretSirenList", cleanAllWhitespaces = true),
      companyName = mapper.string("companyName", trimmed = true),
      companyCountries = mapper.seq("companyCountries"),
      // temporary retrocompat, so we can mep the API safely
      start = mapper.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = mapper.timeWithLocalDateRetrocompatEndOfDay("end"),
      category = mapper.string("category"),
      companyIds = mapper.seq("companyIds").map(UUID.fromString),
      status = mapper.seq("status").map(ReportStatus.withName),
      details = mapper.string("details", trimmed = true),
      hasForeignCountry = mapper.boolean("hasForeignCountry"),
      hasWebsite = mapper.boolean("hasWebsite"),
      hasPhone = mapper.boolean("hasPhone"),
      hasCompany = mapper.boolean("hasCompany"),
      hasAttachment = mapper.boolean("hasAttachment"),
      contactAgreement = mapper.boolean("contactAgreement"),
      withTags = mapper.seq("withTags").map(ReportTag.withName),
      withoutTags = mapper.seq("withoutTags").map(ReportTag.withName),
      activityCodes = mapper.seq("activityCodes"),
      hasResponseEvaluation = mapper.boolean("hasEvaluation"),
      responseEvaluation = mapper.seq("evaluation").map(ResponseEvaluation.withName),
      hasEngagementEvaluation = mapper.boolean("hasEngagementEvaluation"),
      engagementEvaluation = mapper.seq("engagementEvaluation").map(ResponseEvaluation.withName),
      isForeign = mapper.boolean("isForeign"),
      hasBarcode = mapper.boolean("hasBarcode"),
      subcategories = mapper.seq("subcategories"),
      isBookmarked = mapper.boolean("isBookmarked")
    )
  }

  def toReportFilter(reportFilterApi: ReportFilterApi): ReportFilter =
    reportFilterApi
      .into[ReportFilter]
      .enableDefaultValues
      .transform
}
