package repositories.reportmetadata

import models.report.reportmetadata.ReportMetadata
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportMetadataRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[ReportMetadataTable, ReportMetadata]
    with ReportMetadataRepositoryInterface {

  override val table: TableQuery[ReportMetadataTable] = ReportMetadataTable.table
  import dbConfig._

  override def setAssignedUser(reportId: UUID, userId: UUID): Future[Int] = db.run {
    table
      .filter(_.reportId === reportId)
      .map(_.assignedUserId)
      .update(Some(userId))
  }

}

object ReportMetadataRepository {}
