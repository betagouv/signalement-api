package repositories.reportfile

import models.report._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ReportFileRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  def create(file: ReportFile): Future[ReportFile] = db
    .run(ReportFileTable.table += file)
    .map(_ => file)

  def get(uuid: UUID): Future[Option[ReportFile]] = db
    .run(
      ReportFileTable.table
        .filter(_.id === uuid)
        .to[List]
        .result
        .headOption
    )

  def delete(uuid: UUID): Future[Int] = db
    .run(
      ReportFileTable.table
        .filter(_.id === uuid)
        .delete
    )

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile =
      for (refFile <- ReportFileTable.table.filter(_.id.inSet(fileIds)))
        yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]] = db
    .run(
      ReportFileTable.table
        .filter(_.reportId === reportId)
        .to[List]
        .result
    )

  def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]] =
    db.run(
      ReportFileTable.table
        .filter(
          _.reportId inSetBind reportsIds
        )
        .to[List]
        .result
    ).map(events => events.groupBy(_.reportId.get))

  def setAvOutput(fileId: UUID, output: String) = db
    .run(
      ReportFileTable.table
        .filter(_.id === fileId)
        .map(_.avOutput)
        .update(Some(output))
    )

  def removeStorageFileName(fileId: UUID) = db
    .run(
      ReportFileTable.table
        .filter(_.id === fileId)
        .map(_.storageFilename)
        .update("")
    )

}
