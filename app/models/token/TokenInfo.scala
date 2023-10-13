package models.token

import play.api.libs.json.Json
import play.api.libs.json.OWrites
import utils.EmailAddress
import utils.SIRET

sealed trait TokenInfo {
  val token: String
  val emailedTo: EmailAddress
  val kind: TokenKind
}
final case class DGCCRFUserActivationToken(
    token: String,
    kind: TokenKind,
    emailedTo: EmailAddress
) extends TokenInfo

final case class CompanyUserActivationToken(
    token: String,
    kind: TokenKind,
    companySiret: SIRET,
    emailedTo: EmailAddress
) extends TokenInfo

object TokenInfo {

  implicit val DGCCRFUserActivationTokenWrites: OWrites[DGCCRFUserActivationToken] =
    Json.writes[DGCCRFUserActivationToken]
  implicit val CompanyUserActivationTokenWrites: OWrites[CompanyUserActivationToken] =
    Json.writes[CompanyUserActivationToken]
  implicit val TokenInfoWrites: OWrites[TokenInfo] = Json.writes[TokenInfo]

}
