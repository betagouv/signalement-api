package models.website

import play.api.libs.json._

import java.time.OffsetDateTime
import java.util.UUID

case class Website(
    id: WebsiteId = WebsiteId.generateId(),
    creationDate: OffsetDateTime = OffsetDateTime.now,
    host: String,
    companyCountry: Option[String],
    companyId: Option[UUID],
    kind: WebsiteKind = WebsiteKind.PENDING
)

object Website {
  implicit val WebsiteWrites: Writes[Website] = Json.writes[Website]
}
