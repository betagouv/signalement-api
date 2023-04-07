package models.website

import io.scalaland.chimney.dsl.TransformerOps
import models.company.Company

import models.investigation.InvestigationStatus

import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteAndCompany(
    id: UUID,
    creationDate: OffsetDateTime,
    lastUpdated: OffsetDateTime,
    host: String,
    companyCountry: Option[String],
    companyId: Option[UUID],
    identificationStatus: IdentificationStatus,
    // For backward compatibility, to be removed
    kind: String,
    isMarketplace: Boolean,
    investigationStatus: InvestigationStatus,
    company: Option[Company]
)

object WebsiteAndCompany {

  implicit val WebsiteCompanyWrites: Writes[WebsiteAndCompany] = Json.writes[WebsiteAndCompany]

  def toApi(website: Website, maybeCompany: Option[Company]): WebsiteAndCompany =
    website
      .into[WebsiteAndCompany]
      .withFieldConst(_.company, maybeCompany)
      .withFieldConst(_.kind, IdentificationStatus.toKind(website.identificationStatus))
      .transform
}
