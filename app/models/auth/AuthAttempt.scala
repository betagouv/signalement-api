package models.auth

import java.time.OffsetDateTime
import java.util.UUID
import java.time.temporal.ChronoUnit
case class AuthAttempt(
    id: UUID,
    login: String,
    timestamp: OffsetDateTime,
    isSuccess: Option[Boolean],
    failureCause: Option[String] = None
)
object AuthAttempt {
  def build(login: String, isSuccess: Boolean, failureCause: Option[String] = None) =
    AuthAttempt(
      UUID.randomUUID,
      login,
      OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
      Some(isSuccess),
      failureCause
    )
}
