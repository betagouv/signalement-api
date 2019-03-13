package repositories

import java.sql.Date
import java.time.LocalDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.File
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._

  private class FileTable(tag: Tag) extends Table[File](tag, "piece_jointe") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("signalement_id")
    def creationDate = column[LocalDateTime]("date_creation")
    def filename = column[String]("nom")

    type FileData = (UUID, Option[UUID], LocalDateTime, String)

    def constructFile: FileData => File = {
      case (id, reportId, creationDate, filename) => File(id, reportId, creationDate, filename)
    }

    def extractFile: PartialFunction[File, FileData] = {
      case File(id, reportId, creationDate, filename) => (id, reportId, creationDate, filename)
    }

    def * =
      (id, reportId, creationDate, filename) <> (constructFile, extractFile.lift)
  }

  private val fileTableQuery = TableQuery[FileTable]

  def create(file: File): Future[File] = db
    .run(fileTableQuery += file)
    .map(_ => file)

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile = for (refFile <- fileTableQuery.filter(_.id.inSet(fileIds)))
      yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def retrieveReportFiles(reportId: UUID): Future[List[File]] = db
    .run(
      fileTableQuery
        .filter(_.reportId === reportId)
        .to[List].result
    )
}
