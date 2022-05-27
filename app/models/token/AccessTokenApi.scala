package models.token

import models.AccessLevel
import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

case class AccessTokenApi private (
    id: UUID,
    level: Option[AccessLevel],
    emailedTo: Option[EmailAddress],
    expirationDate: Option[OffsetDateTime],
    token: Option[String]
)

object AccessTokenApi {
  def apply(
      id: UUID,
      level: Option[AccessLevel],
      emailedTo: Option[EmailAddress],
      expirationDate: Option[OffsetDateTime],
      token: String,
      userRole: UserRole
  ): AccessTokenApi =
    userRole match {
      case UserRole.Admin => new AccessTokenApi(id, level, emailedTo, expirationDate, Some(token))
      case _              => new AccessTokenApi(id, level, emailedTo, expirationDate, token = None)
    }

  implicit val AccessTokenApiFormat: OFormat[AccessTokenApi] = Json.format[AccessTokenApi]
}
