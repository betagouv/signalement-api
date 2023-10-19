package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID

case class BlacklistedEmailInput(
    email: String,
    comments: String
)

object BlacklistedEmailInput {
  implicit val format: OFormat[BlacklistedEmailInput] = Json.format[BlacklistedEmailInput]
}

case class BlacklistedEmail(
    id: UUID = UUID.randomUUID(),
    email: String,
    comments: String,
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object BlacklistedEmail {
  implicit val format: OFormat[BlacklistedEmail] = Json.format[BlacklistedEmail]

  def fromInput(input: BlacklistedEmailInput) =
    BlacklistedEmail(email = input.email, comments = input.comments)
}
