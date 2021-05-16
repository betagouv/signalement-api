package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{EnterpriseSyncInfo, EnterpriseSyncInfoUpdate}
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnterpriseSyncInfoRepository @Inject()(@NamedDatabase("company_db") dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._

  class EnterpriseSyncInfoTable(tag: Tag) extends Table[EnterpriseSyncInfo](tag, "files_sync_info") {
    def id = column[UUID]("id", O.PrimaryKey)
    def fileName = column[String]("file_name")
    def fileUrl = column[String]("file_url")
    def linesCount = column[Double]("lines_count")
    def linesDone = column[Double]("lines_done")
    def startedAt = column[OffsetDateTime]("started_at")
    def endedAt = column[Option[OffsetDateTime]]("ended_at")
    def errors = column[Option[String]]("errors")

    def * = (
      id,
      fileName,
      fileUrl,
      linesCount,
      linesDone,
      startedAt,
      endedAt,
      errors,
    ) <> (EnterpriseSyncInfo.tupled, EnterpriseSyncInfo.unapply)
  }

  val EnterpriseSyncInfotableQuery = TableQuery[EnterpriseSyncInfoTable]

  def create(info: EnterpriseSyncInfo): Future[EnterpriseSyncInfo] = {
    db.run(EnterpriseSyncInfotableQuery += info).map(_ => info)
  }
  //
  //  def update(id: UUID, entity: EnterpriseSyncInfo): Future[EnterpriseSyncInfo] = {
  //    db.run(EnterpriseSyncInfotableQuery.filter(_.id == id).update(entity)).map(_ => entity)
  //  }

  def byId(id: UUID) = EnterpriseSyncInfotableQuery.filter(_.id === id)

  def updateLinesDone(id: UUID, linesDone: Double): Future[Int] = {
    db.run(byId(id).map(x => x.linesDone).update(linesDone))
  }

  def updateEndedAt(id: UUID, endAt: OffsetDateTime = OffsetDateTime.now()): Future[Int] = {
    db.run(byId(id).map(x => x.endedAt).update(Some(endAt)))
  }

  def updateError(id: UUID, error: String): Future[Int] = {
    db.run(byId(id).map(x => x.errors).update(Some(error)))
  }
}
