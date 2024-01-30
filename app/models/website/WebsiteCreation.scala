package models.website

import models.company.CompanyCreation
import play.api.libs.json.Json
import play.api.libs.json.Reads

case class WebsiteCreation(host: String, company: CompanyCreation)
object WebsiteCreation {
  implicit val reads: Reads[WebsiteCreation] = Json.reads[WebsiteCreation]
}
