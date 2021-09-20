package models.website

import models.Address
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

case class WebsiteUpdateCompany(
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyActivityCode: Option[String]
)

object WebsiteUpdateCompany {
  implicit val format: OFormat[WebsiteUpdateCompany] = Json.format[WebsiteUpdateCompany]
}
