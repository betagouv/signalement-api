package models

import java.time.OffsetDateTime
import java.util.UUID


sealed case class AccessLevel(value: String)

object AccessLevel {
  val NONE = AccessLevel("none")
  val MEMBER = AccessLevel("member")
  val ADMIN = AccessLevel("admin")
}

case class UserAccess(
  companyId: UUID,
  userId: UUID,
  level: AccessLevel,
  updateDate: OffsetDateTime
)

case class AccessToken(
  id: UUID,
  companyId: UUID,
  token: String,
  level: AccessLevel,
  valid: Boolean,
  expirationDate: Option[OffsetDateTime]
)
