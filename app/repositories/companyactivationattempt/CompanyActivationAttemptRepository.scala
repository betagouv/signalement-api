package repositories.companyactivationattempt

import models.company.CompanyActivationAttempt
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class CompanyActivationAttemptRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[CompanyActivationAttemptTable, CompanyActivationAttempt]
    with CompanyActivationAttemptRepositoryInterface {

  override val table: TableQuery[CompanyActivationAttemptTable] = CompanyActivationAttemptTable.table
  import dbConfig._
  val logger: Logger = Logger(this.getClass)

  override def countAttempts(siret: String, delay: Duration): Future[Int] = db
    .run(
      table
        .filter(_.siret === siret)
        .filter(_.timestamp >= OffsetDateTime.now().minusMinutes(delay.toMinutes))
        .length
        .result
    )

  override def listAttempts(siret: String): Future[Seq[CompanyActivationAttempt]] = db
    .run(
      table
        .filter(_.siret === siret)
        .result
    )

}
