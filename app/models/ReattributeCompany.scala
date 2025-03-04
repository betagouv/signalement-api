package models

import models.report.reportmetadata.ReportMetadataDraft
import play.api.libs.json.Json
import play.api.libs.json.Reads
import tasks.company.CompanySearchResult

case class ReattributeCompany(
    company: CompanySearchResult,
    metadata: ReportMetadataDraft
)

object ReattributeCompany {
  implicit val reads: Reads[ReattributeCompany] = Json.reads[ReattributeCompany]
}
