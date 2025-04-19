package models.token

import models.AccessToken
import models.User
import models.UserRole
import models.company.AccessLevel
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

case class ProAccessToken private (
    id: UUID,
    level: Option[AccessLevel],
    emailedTo: Option[EmailAddress],
    expirationDate: Option[OffsetDateTime],
    token: Option[String]
)

object ProAccessToken {

  def apply(token: AccessToken, currentUser: User): ProAccessToken = {
    val proAccessToken = ProAccessToken(
      id = token.id,
      level = token.companyLevel,
      emailedTo = token.emailedTo,
      expirationDate = token.expirationDate,
      token = None
    )
    currentUser.userRole match {
      case UserRole.SuperAdmin | UserRole.Admin => proAccessToken.copy(token = Some(token.token))
      case _                                    => proAccessToken
    }
  }

  implicit val ProAccessTokenFormat: OFormat[ProAccessToken] = Json.format[ProAccessToken]
}
