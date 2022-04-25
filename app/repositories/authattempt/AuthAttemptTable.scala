package repositories.authattempt

import models.auth.AuthAttempt
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID

class AuthAttempTable(tag: Tag) extends Table[AuthAttempt](tag, "auth_attempts") {

  def id = column[UUID]("id", O.PrimaryKey)
  def login = column[String]("login")
  def timestamp = column[OffsetDateTime]("timestamp")
  def isSuccess = column[Option[Boolean]]("is_success")
  def failureCause = column[Option[String]]("failure_cause")

  def * = (id, login, timestamp, isSuccess, failureCause) <> (AuthAttempt.tupled, AuthAttempt.unapply)
}

object AuthAttemptTable {
  val table = TableQuery[AuthAttempTable]
}
