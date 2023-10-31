package models.gs1

import models.gs1.GS1Product.GS1NetContent
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
    netContent: Option[List[GS1NetContent]],
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object GS1Product {
  case class GS1NetContent(
      unitCode: Option[String],
      quantity: Option[String]
  )

  implicit val w: OWrites[GS1NetContent] = Json.writes[GS1NetContent]

  val writesToWebsite: OWrites[GS1Product]   = Json.writes[GS1Product]
  val writesToDashboard: OWrites[GS1Product] = Json.writes[GS1Product]
}
