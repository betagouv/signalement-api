package models

import models.report.reportmetadata.ReportMetadataDraft
import play.api.libs.json.Json
import play.api.libs.json.Reads
import tasks.company.CompanySearchResult

case class ReassignCompany(
    company: CompanySearchResult,
    metadata: ReportMetadataDraft
)

object ReassignCompany {
  implicit val reads: Reads[ReassignCompany] = Json.reads[ReassignCompany]
}
