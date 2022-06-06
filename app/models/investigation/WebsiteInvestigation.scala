package models.investigation

import models.investigation.InvestigationStatus.NotProcessed
import models.website.WebsiteId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime

case class WebsiteInvestigation(
    id: WebsiteInvestigationId,
    websiteId: WebsiteId,
    practice: Option[Practice],
    investigationStatus: InvestigationStatus = NotProcessed,
    attribution: Option[DepartmentDivision],
    creationDate: OffsetDateTime,
    lastUpdated: OffsetDateTime
)

object WebsiteInvestigation {
  implicit val WebsiteInvestigationFormat: OFormat[WebsiteInvestigation] = Json.format[WebsiteInvestigation]
}
