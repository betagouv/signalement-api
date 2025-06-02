package repositories.consumerconsent

import models.consumerconsent.ConsumerConsent
import models.consumerconsent.ConsumerConsentId
import repositories.TypedDatabaseTable
import repositories.consumerconsent.CustomColumnTypes._
import repositories.PostgresProfile.api._
import utils.EmailAddress

import java.time.OffsetDateTime

class ConsumerConsentTable(tag: Tag)
    extends TypedDatabaseTable[ConsumerConsent, ConsumerConsentId](tag, "consumer_consent") {

  def email        = column[EmailAddress]("email")
  def creationDate = column[OffsetDateTime]("creation_date")
  def deletionDate = column[Option[OffsetDateTime]]("deletion_date")

  def pk = primaryKey("pk_consumer_consent", (id, email))

  def * = (id, email, creationDate, deletionDate) <> ((ConsumerConsent.apply _).tupled, ConsumerConsent.unapply)
}

object ConsumerConsentTable {
  val table = TableQuery[ConsumerConsentTable]
}
