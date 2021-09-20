package models.website

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

case class WebsiteUpdate(
    host: Option[String],
    companyCountry: Option[String],
    companyId: Option[UUID],
    kind: Option[WebsiteKind]
) {
  def mergeIn(website: Website): Website =
    website.copy(
      host = host.getOrElse(website.host),
      companyId = companyId,
      companyCountry = companyCountry,
      kind = kind.getOrElse(website.kind)
    )
}

object WebsiteUpdate {
  implicit val format: OFormat[WebsiteUpdate] = Json.format[WebsiteUpdate]
}
