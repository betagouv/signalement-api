package models.token

import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

import java.time.OffsetDateTime

case class AgentAccessToken(
    tokenCreation: OffsetDateTime,
    token: String,
    email: Option[EmailAddress],
    tokenExpirationDate: Option[OffsetDateTime],
    role: UserRole
)

object AgentAccessToken {
  implicit val DGCCRFAccessTokenFormat: OFormat[AgentAccessToken] = Json.format[AgentAccessToken]
}
