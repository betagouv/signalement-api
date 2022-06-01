package models.investigation

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.util.UUID

case class WebsiteInvestigationId(value: UUID) extends AnyVal

object WebsiteInvestigationId {

  implicit val WebsiteInvestigationIdFormat: Format[WebsiteInvestigationId] = Json.valueFormat[WebsiteInvestigationId]

  def generateId() = new WebsiteInvestigationId(UUID.randomUUID())
}
