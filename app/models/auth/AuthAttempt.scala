package models.auth

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID
case class AuthAttempt(
    id: UUID,
    login: String,
    timestamp: OffsetDateTime,
    isSuccess: Option[Boolean],
    failureCause: Option[String] = None
)
object AuthAttempt {

  implicit val authAttemptFormat: OFormat[AuthAttempt] = Json.format[AuthAttempt]

  def build(login: String, isSuccess: Boolean, failureCause: Option[String] = None) =
    AuthAttempt(
      UUID.randomUUID,
      login,
      OffsetDateTime.now(),
      Some(isSuccess),
      failureCause
    )
}
