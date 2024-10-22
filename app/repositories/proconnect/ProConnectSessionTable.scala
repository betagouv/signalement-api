package repositories.proconnect

import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID

class ProConnectSessionTable(tag: Tag) extends DatabaseTable[ProConnectSession](tag, "pro_connect_session") {

  def state = column[String]("state")
  def creationDate = column[OffsetDateTime]("creation_date")

  type ProConnectSessionData = (UUID, String, OffsetDateTime)

  def constructProConnectSession: ProConnectSessionData => ProConnectSession = { case (id, state, creationDate) =>
    ProConnectSession(id, state, creationDate)
  }

  def extractProConnectSession: PartialFunction[ProConnectSession, ProConnectSessionData] = { case ProConnectSession(id, state, creationDate) =>
    (id, state, creationDate)
  }

  def * = (id, state, creationDate) <> (constructProConnectSession, extractProConnectSession.lift)
}

object ProConnectSessionTable {
  val table = TableQuery[ProConnectSessionTable]
}
