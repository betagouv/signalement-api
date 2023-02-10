package models

import play.api.libs.json.Json

import java.time.OffsetDateTime
import java.util.UUID

case class BlacklistedEmail(
    id: UUID = UUID.randomUUID(),
    email: String,
    comments: Option[String],
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object BlacklistedEmail {
  implicit val format = Json.format[BlacklistedEmail]
}
