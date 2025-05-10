package repositories.consumerconsent

import models.consumerconsent.{ConsumerConsent, ConsumerConsentId}
import repositories.TypedDatabaseTable
import repositories.consumerconsent.CustomColumnTypes._
import slick.jdbc.PostgresProfile.api._
import utils.EmailAddress

class ConsumerConsentTable(tag: Tag)
    extends TypedDatabaseTable[ConsumerConsent, ConsumerConsentId](tag, "consumer_consent"){

  def email = column[EmailAddress]("email")

  def pk = primaryKey("pk_consumer_consent", (id, email))

  def * = (id, email) <> ((ConsumerConsent.apply _).tupled, ConsumerConsent.unapply)
}

object ConsumerConsentTable {
  val table = TableQuery[ConsumerConsentTable]
}
