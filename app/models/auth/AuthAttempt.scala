package models.auth

import java.time.OffsetDateTime
import java.util.UUID

case class AuthAttempt(
    id: UUID,
    login: String,
    timestamp: OffsetDateTime,
    isSuccess: Option[Boolean],
    failureCause: Option[String] = None
)
