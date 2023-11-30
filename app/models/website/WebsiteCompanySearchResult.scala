package models.website

import play.api.libs.json.Json
import play.api.libs.json.Writes
import tasks.company.CompanySearchResultApi

case class WebsiteCompanySearchResult(exactMatch: Seq[CompanySearchResultApi], similarHosts: Seq[WebsiteHost])

object WebsiteCompanySearchResult {
  implicit val WebsiteCompanySearchResultWrites: Writes[WebsiteCompanySearchResult] =
    Json.writes[WebsiteCompanySearchResult]
}
