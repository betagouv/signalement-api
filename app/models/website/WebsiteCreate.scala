package models.website

import models.Address
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

case class WebsiteCreate(
    host: String,
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyPostalCode: Option[String],
    companyActivityCode: Option[String]
)

object WebsiteCreate {
  implicit val format: OFormat[WebsiteCreate] = Json.format[WebsiteCreate]
}
