package models.website

import models.investigation.DepartmentDivision
import models.investigation.InvestigationStatus
import models.investigation.Practice
import models.investigation.InvestigationStatus.NotProcessed
import play.api.libs.json._

import java.time.OffsetDateTime
import java.util.UUID

case class Website(
    id: WebsiteId = WebsiteId.generateId(),
    creationDate: OffsetDateTime = OffsetDateTime.now,
    host: String,
    companyCountry: Option[String],
    companyId: Option[UUID],
    kind: WebsiteKind = WebsiteKind.PENDING,
    practice: Option[Practice] = None,
    investigationStatus: InvestigationStatus = NotProcessed,
    attribution: Option[DepartmentDivision] = None,
    lastUpdated: OffsetDateTime = OffsetDateTime.now
)

object Website {
  implicit val WebsiteWrites: Writes[Website] = Json.writes[Website]
}
