package repositories.blacklistedemails

import models.BlacklistedEmail
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime

class BlacklistedEmailsTable(tag: Tag) extends DatabaseTable[BlacklistedEmail](tag, "blacklisted_emails") {
  def email = column[String]("email")
  def comments = column[Option[String]]("comments")
  def creationDate = column[OffsetDateTime]("creation_date")

  def * = (
    id,
    email,
    comments,
    creationDate
  ) <> ((BlacklistedEmail.apply _).tupled, BlacklistedEmail.unapply)
}

object BlacklistedEmailsTable {
  val table = TableQuery[BlacklistedEmailsTable]
}
