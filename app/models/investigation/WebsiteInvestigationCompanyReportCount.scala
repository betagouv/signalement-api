package models.investigation

import io.scalaland.chimney.dsl.TransformerOps
import models.Company
import models.investigation.InvestigationStatus.NotProcessed
import models.website.Website
import models.website.WebsiteId
import models.website.WebsiteKind
import play.api.libs.json.Json
import play.api.libs.json.Writes
import utils.Country

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteInvestigationCompanyReportCount(
    id: Option[WebsiteInvestigationId],
    websiteId: WebsiteId,
    creationDate: OffsetDateTime,
    host: String,
    companyId: Option[UUID],
    companyCountry: Option[Country],
    kind: WebsiteKind,
    company: Option[Company],
    count: Int,
    practice: Option[Practice],
    investigation: InvestigationStatus = NotProcessed,
    attribution: Option[DepartmentDivision]
)

object WebsiteInvestigationCompanyReportCount {

  implicit val WebsiteInvestigationCompanyReportCountWrites: Writes[WebsiteInvestigationCompanyReportCount] =
    Json.writes[WebsiteInvestigationCompanyReportCount]

  def toApi(
      countByWebsiteCompany: (((Website, Option[WebsiteInvestigation]), Option[Company]), Int)
  ): WebsiteInvestigationCompanyReportCount = {
    val (((website, websiteInvestigation), maybeCompany), count) = countByWebsiteCompany
    website
      .into[WebsiteInvestigationCompanyReportCount]
      .withFieldComputed(_.websiteId, _.id)
      .withFieldConst(_.id, websiteInvestigation.map(_.id))
      .withFieldConst(_.company, maybeCompany)
      .withFieldConst(_.companyCountry, website.companyCountry.map(Country.fromName))
      .withFieldConst(_.count, count)
      .withFieldConst(_.practice, websiteInvestigation.flatMap(_.practice))
      .withFieldConst(_.investigation, websiteInvestigation.map(_.investigation).getOrElse(NotProcessed))
      .withFieldConst(_.attribution, websiteInvestigation.flatMap(_.attribution))
      .transform
  }
}
