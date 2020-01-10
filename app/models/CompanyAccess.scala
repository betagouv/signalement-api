package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.{EmailAddress, SIRET}


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
  implicit val writes = new Writes[AccessLevel] {
    def writes(level: AccessLevel) = Json.toJson(level.value)
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
