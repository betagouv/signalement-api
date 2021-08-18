package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.EmailAddress
import utils.SIRET

sealed case class TokenKind(value: String)

object TokenKind {
  val COMPANY_INIT = TokenKind("COMPANY_INIT")
  val COMPANY_JOIN = TokenKind("COMPANY_JOIN")
  val DGCCRF_ACCOUNT = TokenKind("DGCCRF_ACCOUNT")
  val VALIDATE_EMAIL = TokenKind("VALIDATE_EMAIL")

  def fromValue(v: String) =
    List(COMPANY_INIT, COMPANY_JOIN, DGCCRF_ACCOUNT, VALIDATE_EMAIL).find(_.value == v).head
  implicit val reads = new Reads[TokenKind] {
    def reads(json: JsValue): JsResult[TokenKind] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[TokenKind] {
    def writes(kind: TokenKind) = Json.toJson(kind.value)
  }
}

case class AccessToken(
    id: UUID,
    creationDate: OffsetDateTime,
    kind: TokenKind,
    token: String,
    valid: Boolean,
    companyId: Option[UUID],
    companyLevel: Option[AccessLevel],
    emailedTo: Option[EmailAddress],
    expirationDate: Option[OffsetDateTime]
)

case class TokenInfo(
    token: String,
    kind: TokenKind,
    companySiret: Option[SIRET],
    emailedTo: Option[EmailAddress]
)
object TokenInfo {
  implicit val write = Json.writes[TokenInfo]
}

case class ActivationRequest(
    draftUser: DraftUser,
    token: String,
    companySiret: Option[SIRET]
)
object ActivationRequest {
  implicit val ActivationRequestFormat: OFormat[ActivationRequest] = Json.format[ActivationRequest]
}
