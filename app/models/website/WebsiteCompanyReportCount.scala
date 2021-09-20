package models.website

import io.scalaland.chimney.dsl.TransformerOps
import models.Company
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompanyReportCount(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    companyId: Option[UUID],
    country: Option[String],
    kind: WebsiteKind,
    company: Company,
    count: Int
)

object WebsiteCompanyReportCount {

  implicit val WebsiteCompanyCountWrites: Writes[WebsiteCompanyReportCount] = Json.writes[WebsiteCompanyReportCount]

  def toApi(countByWebsiteCompany: ((Website, Company), Int)): WebsiteCompanyReportCount = {
    val ((website, company), count) = countByWebsiteCompany
    website
      .into[WebsiteCompanyReportCount]
      .withFieldConst(_.company, company)
      .withFieldConst(_.count, count)
      .transform
  }
}
