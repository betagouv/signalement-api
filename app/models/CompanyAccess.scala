package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.{EmailAddress, SIRET}


case class AccessToken(
  id: UUID,
  companyId: UUID,
  token: String,
  level: AccessLevel,
  valid: Boolean,
  emailedTo: Option[EmailAddress],
  expirationDate: Option[OffsetDateTime]
)

case class TokenInfo(
  token: String,
  companySiret: SIRET,
  emailedTo: Option[EmailAddress]
)
object TokenInfo {
  implicit val write = Json.writes[TokenInfo]
}

case class ActivationRequest(
  draftUser: DraftUser,
  tokenInfo: TokenInfo
)
object ActivationRequest {
  implicit val tokenInfoFormat = Json.format[TokenInfo]
  implicit val ActivationRequestFormat = Json.format[ActivationRequest]
}
