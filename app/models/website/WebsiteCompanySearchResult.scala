package models.website

import play.api.libs.json.Json
import play.api.libs.json.Writes
import tasks.company.CompanySearchResult

case class WebsiteCompanySearchResult(exactMatch: Seq[CompanySearchResult], similarHosts: Seq[WebsiteHost])

object WebsiteCompanySearchResult {
  implicit val WebsiteCompanySearchResultWrites: Writes[WebsiteCompanySearchResult] =
    Json.writes[WebsiteCompanySearchResult]
}
