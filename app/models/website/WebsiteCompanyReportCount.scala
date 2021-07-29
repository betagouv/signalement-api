package models.website

import models.Company
import models.Website
import models.WebsiteKind
import play.api.libs.json.Json
import play.api.libs.json.Writes
import io.scalaland.chimney.dsl.TransformerOps
import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompanyReportCount(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    companyId: UUID,
    kind: WebsiteKind,
    company: Company,
    count: Int
)

object WebsiteCompanyReportCount {

  implicit val WebsiteCompanyCountWrites: Writes[WebsiteCompanyReportCount] = Json.writes[WebsiteCompanyReportCount]

  def toDomain(countByWebsiteCompany: ((Website, Company), Int)): WebsiteCompanyReportCount = {
    val ((website, company), count) = countByWebsiteCompany
    website
      .into[WebsiteCompanyReportCount]
      .withFieldConst(_.company, company)
      .withFieldConst(_.count, count)
      .transform
  }
}
