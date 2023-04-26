package models.report

import models.UserRole
import models.UserRole.Admin
import models.UserRole.DGCCRF
import models.report.ReportStatus.LanceurAlerte
import models.report.ReportTag.ReportTagHiddenToProfessionnel
import models.report.review.ResponseEvaluation
import play.api.libs.json.Reads
import utils.QueryStringMapper
import utils.URL

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

case class ReportFilter(
    departments: Seq[String] = Seq.empty,
    email: Option[String] = None,
    websiteURL: Option[String] = None,
    phone: Option[String] = None,
    siretSirenList: Seq[String] = Seq.empty,
    siretSirenDefined: Option[Boolean] = None,
    companyIds: Seq[UUID] = Seq.empty,
    companyName: Option[String] = None,
    companyCountries: Seq[String] = Seq.empty,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None,
    category: Option[String] = None,
    status: Seq[ReportStatus] = Seq.empty,
    details: Option[String] = None,
    description: Option[String] = None,
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
    hasEvaluation: Option[Boolean] = None,
    evaluation: Seq[ResponseEvaluation] = Seq.empty,
    fullText: Option[String] = None
)

object ReportFilter {

  private[models] def hostFromWebsiteFilter(websiteFilter: Option[String]) =
    websiteFilter.flatMap(website => URL(website).getHost.orElse(websiteFilter))

  def fromQueryString(q: Map[String, Seq[String]], userRole: UserRole): Try[ReportFilter] = Try {
    val mapper = new QueryStringMapper(q)
    ReportFilter(
      departments = mapper.seq("departments"),
      email = mapper.string("email"),
      websiteURL = hostFromWebsiteFilter(mapper.string("websiteURL")),
      phone = mapper.string("phone"),
      siretSirenList = mapper.seq("siretSirenList"),
      companyName = mapper.string("companyName"),
      companyCountries = mapper.seq("companyCountries"),
      // temporary retrocompat, so we can mep the API safely
      start = mapper.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = mapper.timeWithLocalDateRetrocompatEndOfDay("end"),
      category = mapper.string("category"),
      companyIds = mapper.seq("companyIds").map(UUID.fromString),
      status = ReportStatus.filterByUserRole(
        mapper.seq("status").map(ReportStatus.withName),
        userRole
      ),
      details = mapper.string("details"),
      description = mapper.string("description"),
      employeeConsumer = userRole match {
        case Admin  => None
        case DGCCRF => None
        case _      => Some(false)
      },
      hasForeignCountry = mapper.boolean("hasForeignCountry"),
      hasWebsite = mapper.boolean("hasWebsite"),
      hasPhone = mapper.boolean("hasPhone"),
      hasCompany = mapper.boolean("hasCompany"),
      hasAttachment = mapper.boolean("hasAttachment"),
      contactAgreement = mapper.boolean("contactAgreement"),
      withTags = mapper.seq("withTags").map(ReportTag.withName),
      withoutTags = mapper.seq("withoutTags").map(ReportTag.withName),
      activityCodes = mapper.seq("activityCodes"),
      hasEvaluation = mapper.boolean("hasEvaluation"),
      evaluation = mapper.seq("evaluation").map(ResponseEvaluation.withName),
      fullText = mapper.string("fullText")
    )
  }

  val allReportsFilter = ReportFilter()

  val transmittedReportsFilter = ReportFilter(
    status = ReportStatus.values.filterNot(_ == LanceurAlerte),
    withoutTags = ReportTagHiddenToProfessionnel,
    siretSirenDefined = Some(true)
  )

  implicit val reportFilterReads: Reads[ReportFilter] = Reads { jsValue =>
    for {
      departments <- (jsValue \ "departments").validateOpt[Seq[String]]
      email <- (jsValue \ "email").validateOpt[String]
      websiteURL <- (jsValue \ "websiteURL").validateOpt[String]
      phone <- (jsValue \ "phone").validateOpt[String]
      siretSirenList <- (jsValue \ "siretSirenList").validateOpt[Seq[String]]
      companyIds <- (jsValue \ "companyIds").validateOpt[Seq[UUID]]
      companyName <- (jsValue \ "companyName").validateOpt[String]
      companyCountries <- (jsValue \ "companyCountries").validateOpt[Seq[String]]
      category <- (jsValue \ "category").validateOpt[String]
      status <- (jsValue \ "status").validateOpt[Seq[String]]
      details <- (jsValue \ "details").validateOpt[String]
      description <- (jsValue \ "description").validateOpt[String]
      contactAgreement <- (jsValue \ "contactAgreement").validateOpt[Boolean]
      hasForeignCountry <- (jsValue \ "hasForeignCountry").validateOpt[Boolean]
      hasWebsite <- (jsValue \ "hasWebsite").validateOpt[Boolean]
      hasPhone <- (jsValue \ "hasPhone").validateOpt[Boolean]
      hasCompany <- (jsValue \ "hasCompany").validateOpt[Boolean]
      hasAttachment <- (jsValue \ "hasAttachment").validateOpt[Boolean]
      withTags <- (jsValue \ "withTags").validateOpt[Seq[String]]
      withoutTags <- (jsValue \ "withoutTags").validateOpt[Seq[String]]
      activityCodes <- (jsValue \ "activityCodes").validateOpt[Seq[String]]
    } yield ReportFilter(
      departments = departments.getOrElse(Seq.empty),
      email = email,
      websiteURL = websiteURL,
      phone = phone,
      siretSirenList = siretSirenList.getOrElse(Seq.empty),
      siretSirenDefined = None,
      companyIds = companyIds.getOrElse(Seq.empty),
      companyName = companyName,
      companyCountries = companyCountries.getOrElse(Seq.empty),
      start = None,
      end = None,
      category = category,
      status = status.getOrElse(Seq.empty).map(ReportStatus.withName),
      details = details,
      description = description,
      employeeConsumer = None,
      contactAgreement = contactAgreement,
      hasForeignCountry = hasForeignCountry,
      hasWebsite = hasWebsite,
      hasPhone = hasPhone,
      hasCompany = hasCompany,
      hasAttachment = hasAttachment,
      withTags = withTags.getOrElse(Seq.empty).map(ReportTag.withName),
      withoutTags = withoutTags.getOrElse(Seq.empty).map(ReportTag.withName),
      activityCodes = activityCodes.getOrElse(Seq.empty)
    )
  }
}
