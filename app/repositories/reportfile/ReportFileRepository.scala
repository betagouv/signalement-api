package repositories.reportfile

import models.report._
import models.report.reportfile.ReportFileId
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.TypedCRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.ResultSetConcurrency
import slick.jdbc.ResultSetType
import repositories.reportfile.ReportFileColumnType._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportFileRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[ReportFileTable, ReportFile, ReportFileId]
    with ReportFileRepositoryInterface {

  override val table: TableQuery[ReportFileTable] = ReportFileTable.table
  import dbConfig._

  override def attachFilesToReport(fileIds: List[ReportFileId], reportId: UUID): Future[Int] = {
    val queryFile =
      for (refFile <- table.filter(_.id.inSet(fileIds)))
        yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  override def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]] = db
    .run(
      table
        .filter(_.reportId === reportId)
        .to[List]
        .result
    )

  override def reportsFiles(reportFiles: List[ReportFileId]): Future[List[ReportFile]] =
    db.run(
      table
        .filter(
          _.id inSetBind reportFiles
        )
        .to[List]
        .result
    )

  override def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]] =
    db.run(
      table
        .filter(
          _.reportId inSetBind reportsIds
        )
        .to[List]
        .result
    ).map(events => events.groupBy(_.reportId.get))

  override def setAvOutput(fileId: ReportFileId, output: String): Future[Int] = db
    .run(
      table
        .filter(_.id === fileId)
        .map(_.avOutput)
        .update(Some(output))
    )

  override def count(filter: ReportFileFilter): Future[Int] = db.run(
    table
      .filterOpt(filter.start) { case (table, start) =>
        table.creationDate >= start
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.creationDate <= end
      }
      .filterOpt(filter.origin) { case (table, origin) =>
        table.origin === origin
      }
      .length
      .result
  )

  def streamOrphanReportFiles: Source[ReportFile, NotUsed] = Source
    .fromPublisher(
      db.stream(
        table
          .filter(_.reportId.isEmpty)
          .filter(_.creationDate <= OffsetDateTime.now().minusDays(7))
          .result
          .withStatementParameters(
            rsType = ResultSetType.ForwardOnly,
            rsConcurrency = ResultSetConcurrency.ReadOnly,
            fetchSize = 10000
          )
          .transactionally
      )
    )
    .log("user")
}
