package repositories.consumerconsent

import models.consumerconsent.{ConsumerConsent, ConsumerConsentId}
import repositories.TypedCRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.consumerconsent.CustomColumnTypes._

import scala.concurrent.ExecutionContext

class ConsumerConsentRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[ConsumerConsentTable, ConsumerConsent, ConsumerConsentId]
    with ConsumerConsentRepositoryInterface {

  override val table = ConsumerConsentTable.table
}
