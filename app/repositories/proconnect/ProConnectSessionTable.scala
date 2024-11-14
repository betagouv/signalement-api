package repositories.proconnect

import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID

class ProConnectSessionTable(tag: Tag) extends DatabaseTable[ProConnectSession](tag, "pro_connect_session") {

  def state        = column[String]("state")
  def nonce        = column[String]("nonce")
  def creationDate = column[OffsetDateTime]("creation_date")

  type ProConnectSessionData = (UUID, String, String, OffsetDateTime)

  def constructProConnectSession: ProConnectSessionData => ProConnectSession = {
    case (id, state, nonce, creationDate) =>
      ProConnectSession(id, state, nonce, creationDate)
  }

  def extractProConnectSession: PartialFunction[ProConnectSession, ProConnectSessionData] = {
    case ProConnectSession(id, state, nonce, creationDate) =>
      (id, state, nonce, creationDate)
  }

  def * = (id, state, nonce, creationDate) <> (constructProConnectSession, extractProConnectSession.lift)
}

object ProConnectSessionTable {
  val table = TableQuery[ProConnectSessionTable]
}
