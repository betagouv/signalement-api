package models.website

import io.scalaland.chimney.dsl.TransformerOps
import models.Company
import play.api.libs.json.Json
import play.api.libs.json.Writes
import utils.Country

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompanyReportCount(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    companyId: Option[UUID],
    companyCountry: Option[Country],
    kind: WebsiteKind,
    company: Option[Company],
    count: Int
)

object WebsiteCompanyReportCount {

  implicit val WebsiteCompanyCountWrites: Writes[WebsiteCompanyReportCount] = Json.writes[WebsiteCompanyReportCount]

  def toApi(countByWebsiteCompany: ((Website, Option[Company]), Int)): WebsiteCompanyReportCount = {
    val ((website, maybeCompany), count) = countByWebsiteCompany
    website
      .into[WebsiteCompanyReportCount]
      .withFieldConst(_.company, maybeCompany)
      .withFieldConst(_.companyCountry, website.companyCountry.map(Country.fromName))
      .withFieldConst(_.count, count)
      .transform
  }
}
