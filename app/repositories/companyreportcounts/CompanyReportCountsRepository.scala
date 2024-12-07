package repositories.companyreportcounts

import cats.implicits.toFunctorOps
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class CompanyReportCountsRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[CompanyReportCountsTable, CompanyReportCounts]
    with CompanyReportCountsRepositoryInterface {

  override val table: TableQuery[CompanyReportCountsTable] = CompanyReportCountsTable.table

  import dbConfig._
  import repositories.PostgresProfile.api._

  override def refreshMaterializeView(): Future[Unit] =
    db.run(
      sql"""REFRESH MATERIALIZED VIEW CONCURRENTLY company_report_counts;""".as[Unit]
    ).void
}
