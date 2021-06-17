package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.Inject
import javax.inject.Singleton
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AsyncFileRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._

  class AsyncFileTable(tag: Tag) extends Table[AsyncFile](tag, "async_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[Option[String]]("filename")
    def storageFilename = column[Option[String]]("storage_filename")

    def * = (id, userId, creationDate, filename, storageFilename) <> (AsyncFile.tupled, AsyncFile.unapply)
  }

  val AsyncFileTableQuery = TableQuery[AsyncFileTable]

  def create(owner: User): Future[AsyncFile] =
    db.run(
      AsyncFileTableQuery returning AsyncFileTableQuery += AsyncFile(
        id = UUID.randomUUID(),
        userId = owner.id,
        creationDate = OffsetDateTime.now,
        filename = None,
        storageFilename = None
      )
    )

  def update(uuid: UUID, filename: String, storageFilename: String): Future[Int] =
    db.run(
      AsyncFileTableQuery
        .filter(_.id === uuid)
        .map(f => (f.filename, f.storageFilename))
        .update((Some(filename), Some(storageFilename)))
    )

  def list(user: User): Future[List[AsyncFile]] =
    db.run(
      AsyncFileTableQuery
        .filter(_.userId === user.id)
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )
}
