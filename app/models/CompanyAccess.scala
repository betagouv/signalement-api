package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json._


sealed case class AccessLevel(value: String, display: String)

object AccessLevel {
  val NONE = AccessLevel("none", "Aucun accès")
  val MEMBER = AccessLevel("member", "Accès simple")
  val ADMIN = AccessLevel("admin", "Administrateur")

  def fromValue(v: String) = {
    List(NONE, MEMBER, ADMIN).find(_.value == v).getOrElse(NONE)
  }
}

case class UserAccess(
  companyId: UUID,
  userId: UUID,
  level: AccessLevel,
  updateDate: OffsetDateTime
)

case class AccessToken(
  id: UUID,
  companyId: UUID,
  token: String,
  level: AccessLevel,
  valid: Boolean,
  expirationDate: Option[OffsetDateTime]
)

case class TokenInfo(
  token: String,
  companySiret: String
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
