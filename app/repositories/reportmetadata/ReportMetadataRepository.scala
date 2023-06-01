package repositories.reportmetadata

import models.report.reportmetadata.ReportMetadata
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class ReportMetadataRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[ReportMetadataTable, ReportMetadata]
    with ReportMetadataRepositoryInterface {

  override val table: TableQuery[ReportMetadataTable] = ReportMetadataTable.table

}

object ReportMetadataRepository {}
