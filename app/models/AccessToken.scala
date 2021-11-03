package models

import models.token.TokenKind
import play.api.libs.json._
import utils.EmailAddress
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID

case class AccessToken(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime,
    kind: TokenKind,
    token: String,
    valid: Boolean,
    companyId: Option[UUID],
    companyLevel: Option[AccessLevel],
    emailedTo: Option[EmailAddress],
    expirationDate: Option[OffsetDateTime]
)

case class ActivationRequest(
    draftUser: DraftUser,
    token: String,
    companySiret: Option[SIRET]
)
object ActivationRequest {
  implicit val ActivationRequestFormat: OFormat[ActivationRequest] = Json.format[ActivationRequest]
}
