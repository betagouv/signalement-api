package models.website

import io.scalaland.chimney.dsl._
import models.company.Company
import models.investigation.InvestigationStatus
import play.api.libs.json.Json
import play.api.libs.json.Writes
import tasks.website.ExtractionResultApi
import utils.Country

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompanyReportCount(
    id: WebsiteId,
    creationDate: OffsetDateTime,
    lastUpdated: OffsetDateTime,
    host: String,
    companyId: Option[UUID],
    companyCountry: Option[Country],
    isMarketplace: Boolean,
    identificationStatus: IdentificationStatus,
    // For backward compatibility, to be removed
    kind: String,
    company: Option[Company],
    investigationStatus: InvestigationStatus,
    count: Int,
    siretExtraction: Option[ExtractionResultApi]
)

object WebsiteCompanyReportCount {

  implicit val WebsiteCompanyCountWrites: Writes[WebsiteCompanyReportCount] = Json.writes[WebsiteCompanyReportCount]

  def toApi(
      countByWebsiteCompany: (Website, Option[Company], Option[ExtractionResultApi], Int)
  ): WebsiteCompanyReportCount = {
    val (website, maybeCompany, maybeSiretExtraction, count) = countByWebsiteCompany
    website
      .into[WebsiteCompanyReportCount]
      .withFieldComputed(_.id, _.id)
      .withFieldConst(_.company, maybeCompany)
      .withFieldConst(_.companyCountry, website.companyCountry.map(Country.fromCode(_)))
      .withFieldConst(_.count, count)
      .withFieldConst(_.kind, IdentificationStatus.toKind(website.identificationStatus))
      .withFieldConst(_.siretExtraction, maybeSiretExtraction)
      .transform
  }
}
