package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AsyncFileRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._

  class AsyncFileTable(tag: Tag) extends Table[AsyncFile](tag, "async_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[String]("filename")
    def storageFilename = column[String]("storage_filename")

    def * = (id, userId, creationDate, filename, storageFilename) <> (AsyncFile.tupled, AsyncFile.unapply)
  }

  val AsyncFileTableQuery = TableQuery[AsyncFileTable]

  def list(user: User): Future[List[AsyncFile]] =
    db.run(AsyncFileTableQuery
      .filter(_.userId === user.id)
      .to[List]
      .result
    )
}
