package models.website

import models.CompanyCreation
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class WebsiteCreate(
    host: String,
    company: Option[CompanyCreation],
    companyCountry: Option[String]
)

object WebsiteCreate {
  implicit val format: OFormat[WebsiteCreate] = Json.format[WebsiteCreate]
}
