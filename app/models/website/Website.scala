package models.website

import models.investigation.InvestigationStatus
import models.investigation.InvestigationStatus.NotProcessed
import play.api.libs.json._

import java.time.OffsetDateTime
import java.util.UUID

case class Website(
    id: WebsiteId = WebsiteId.generateId(),
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    host: String,
    isMarketplace: Boolean = false,
    companyCountry: Option[String],
    companyId: Option[UUID],
    identificationStatus: IdentificationStatus = IdentificationStatus.NotIdentified,
    investigationStatus: InvestigationStatus = NotProcessed,
    lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

object Website {
  implicit val WebsiteWrites: Writes[Website] = Json.writes[Website]
}
