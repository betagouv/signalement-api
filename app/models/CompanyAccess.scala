package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json._


sealed case class AccessLevel(value: String)

object AccessLevel {
  val NONE = AccessLevel("none")
  val MEMBER = AccessLevel("member")
  val ADMIN = AccessLevel("admin")

  def fromValue(v: String) = {
    List(NONE, MEMBER, ADMIN).find(_.value == v).getOrElse(NONE)
  }
  implicit val reads = new Reads[AccessLevel] {
    def reads(json: JsValue): JsResult[AccessLevel] = json.validate[String].map(fromValue(_))
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
