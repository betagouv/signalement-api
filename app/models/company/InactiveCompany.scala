package models.company

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class InactiveCompany(company: Company, ignoredReportCount: Int)

object InactiveCompany {
  implicit val InactiveCompanyFormat: OFormat[InactiveCompany] = Json.format[InactiveCompany]
}
