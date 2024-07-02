package models.auth

import java.time.OffsetDateTime
case class AuthAttemptFilter(
    isSuccess: Option[Boolean] = None,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None
)
