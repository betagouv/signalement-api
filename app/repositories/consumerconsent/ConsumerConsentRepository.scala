package repositories.consumerconsent

import models.consumerconsent.ConsumerConsent
import models.consumerconsent.ConsumerConsentId
import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import repositories.consumerconsent.CustomColumnTypes._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ConsumerConsentRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[ConsumerConsentTable, ConsumerConsent, ConsumerConsentId]
    with ConsumerConsentRepositoryInterface {

  override val table = ConsumerConsentTable.table

  import dbConfig._

  override def removeConsent(emailAddress: EmailAddress): Future[Unit] = db
    .run(
      table
        .filter(_.email === emailAddress)
        .filter(_.deletionDate.isEmpty)
        .map(_.deletionDate)
        .update(Some(OffsetDateTime.now()))
    )
    .map(_ => ())

}
