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

  def update(id: UUID, entity: EnterpriseSyncInfoUpdate): Future[Option[EnterpriseSyncInfo]] = {
    val byId = EnterpriseSyncInfotableQuery.filter(_.id === id)
    for {
      infoOpt <- db.run(byId.result.headOption)
        .map(infoOption => infoOption.map(info => info.copy(
          linesDone = entity.linesDone.getOrElse(info.linesDone),
          endedAt = entity.endedAt.orElse(info.endedAt),
          errors = entity.errors.orElse(info.errors),
        )))
      updatedInfo <- infoOpt.map(info => db.run(byId.update(info)).map(_ => Some(info))).getOrElse(Future(None))
    } yield updatedInfo
  }
}
