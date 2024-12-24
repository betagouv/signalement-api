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

  override def setAssignedUser(reportId: UUID, userId: UUID): Future[ReportMetadata] = {
    val action = (for {
      existingMetadata <- table.filter(_.reportId === reportId).result.headOption
      newOrUpdatedMetadata = existingMetadata
        .map(_.copy(assignedUserId = Some(userId)))
        .getOrElse(
          ReportMetadata(reportId = reportId, isMobileApp = false, os = None, assignedUserId = Some(userId), None)
        )
      _ <-
        table.insertOrUpdate(newOrUpdatedMetadata)
    } yield newOrUpdatedMetadata).transactionally
    db.run(action)
  }

}

object ReportMetadataRepository {}
