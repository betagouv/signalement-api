package repositories.asyncfiles

import models.AsyncFile
import models.AsyncFileKind
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID
import repositories.asyncfiles.AsyncFilesColumnType._

class AsyncFilesTable(tag: Tag) extends Table[AsyncFile](tag, "async_files") {

  def id = column[UUID]("id", O.PrimaryKey)
  def userId = column[UUID]("user_id")
  def creationDate = column[OffsetDateTime]("creation_date")
  def filename = column[Option[String]]("filename")
  def kind = column[AsyncFileKind]("kind")
  def storageFilename = column[Option[String]]("storage_filename")

  def * = (id, userId, creationDate, filename, kind, storageFilename) <> (AsyncFile.tupled, AsyncFile.unapply)
}

object AsyncFilesTable {
  val Table = TableQuery[AsyncFilesTable]
}
