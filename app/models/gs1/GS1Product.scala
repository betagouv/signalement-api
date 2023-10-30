package models.gs1

import play.api.libs.json.Json
import play.api.libs.json.OWrites
import utils.SIREN

import java.time.OffsetDateTime
import java.util.UUID

case class GS1Product(
    id: UUID = UUID.randomUUID(),
    gtin: String,
    siren: Option[SIREN],
    description: Option[String],
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object GS1Product {
  implicit val writes: OWrites[GS1Product] = Json.writes[GS1Product]
}
