package models.report

import models.report.review.ResponseEvaluation

import java.time.OffsetDateTime
import java.util.UUID

case class ReportFilter(
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
    fullText: Option[String] = None,
    visibleToPro: Option[Boolean] = None,
    isForeign: Option[Boolean] = None,
    hasBarcode: Option[Boolean] = None,
    assignedUserId: Option[UUID] = None,
    isBookmarked: Option[Boolean] = None
)

object ReportFilter {
  val transmittedReportsFilter: ReportFilter = ReportFilter(
    visibleToPro = Some(true)
  )
}
