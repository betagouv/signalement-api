package models

import models.company.AccessLevel
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
    expirationDate: Option[OffsetDateTime],
    userId: Option[UUID]
)

object AccessToken {

  def build(
      kind: TokenKind,
      token: String,
      validity: Option[java.time.temporal.TemporalAmount],
      companyId: Option[UUID],
      level: Option[AccessLevel],
      emailedTo: Option[EmailAddress] = None,
      creationDate: OffsetDateTime = OffsetDateTime.now(),
      userId: Option[UUID] = None
  ): AccessToken = AccessToken(
    creationDate = creationDate,
    kind = kind,
    token = token,
    valid = true,
    companyId = companyId,
    companyLevel = level,
    emailedTo = emailedTo,
    expirationDate = validity.map(OffsetDateTime.now().plus(_)),
    userId = userId
  )

  def resetExpirationDate(accessToken: AccessToken, validity: java.time.temporal.TemporalAmount) =
    accessToken.copy(expirationDate = Some(OffsetDateTime.now().plus(validity)))

}

case class ActivationRequest(
    draftUser: DraftUser,
    token: String,
    companySiret: Option[SIRET]
)
object ActivationRequest {
  implicit val ActivationRequestFormat: OFormat[ActivationRequest] = Json.format[ActivationRequest]
}

case class InvitationRequest(
    email: EmailAddress
)
object InvitationRequest {
  implicit val InvitationRequestFormat: OFormat[InvitationRequest] = Json.format[InvitationRequest]
}
