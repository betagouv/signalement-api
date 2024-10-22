package repositories.proconnect

import models.auth.AuthToken
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID

class ProConnectSessionTable(tag: Tag) extends DatabaseTable[AuthToken](tag, "proconnect_session") {

  def userId = column[UUID]("state")
  def expiry = column[OffsetDateTime]("expiry")

  type AuthTokenData = (UUID, UUID, OffsetDateTime)

  def constructAuthToken: AuthTokenData => AuthToken = { case (id, userId, expiry) =>
    AuthToken(id, userId, expiry)
  }

  def extractAuthToken: PartialFunction[AuthToken, AuthTokenData] = { case AuthToken(id, userId, expiry) =>
    (id, userId, expiry)
  }

  def * = (id, userId, expiry) <> (constructAuthToken, extractAuthToken.lift)
}

object ProConnectSessionTable {
  val table = TableQuery[ProConnectSessionTable]
}
