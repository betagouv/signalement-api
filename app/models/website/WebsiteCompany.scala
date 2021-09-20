package models.website

import io.scalaland.chimney.dsl.TransformerOps
import models.Company
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompany(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    country: Option[String],
    companyId: Option[UUID],
    kind: WebsiteKind,
    company: Option[Company]
)

object WebsiteCompany {

  implicit val WebsiteCompanyWrites: Writes[WebsiteCompany] = Json.writes[WebsiteCompany]

  def toApi(website: Website, maybeCompany: Option[Company]): WebsiteCompany =
    website
      .into[WebsiteCompany]
      .withFieldConst(_.company, maybeCompany)
      .transform
}
