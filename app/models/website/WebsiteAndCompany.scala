package models.website

import io.scalaland.chimney.dsl.TransformerOps
import models.Company
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteAndCompany(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    companyCountry: Option[String],
    companyId: Option[UUID],
    kind: WebsiteKind,
    company: Option[Company]
)

object WebsiteAndCompany {

  implicit val WebsiteCompanyWrites: Writes[WebsiteAndCompany] = Json.writes[WebsiteAndCompany]

  def toApi(website: Website, maybeCompany: Option[Company]): WebsiteAndCompany =
    website
      .into[WebsiteAndCompany]
      .withFieldConst(_.company, maybeCompany)
      .transform
}
