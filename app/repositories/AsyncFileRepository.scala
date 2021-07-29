package repositories

import java.time.OffsetDateTime
import java.util.UUID

import enumeratum.SlickEnumSupport
import javax.inject.Inject
import javax.inject.Singleton
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AsyncFileRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends SlickEnumSupport {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import PostgresProfile.api._
  override val profile: slick.jdbc.JdbcProfile = dbConfigProvider.get.profile

  implicit lazy val asyncFileKindMapper = mappedColumnTypeForEnum(AsyncFileKind)

  class AsyncFileTable(tag: Tag) extends Table[AsyncFile](tag, "async_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[Option[String]]("filename")
    def kind = column[AsyncFileKind]("kind")
    def storageFilename = column[Option[String]]("storage_filename")

    def * = (id, userId, creationDate, filename, kind, storageFilename) <> (AsyncFile.tupled, AsyncFile.unapply)
  }

  val AsyncFileTableQuery = TableQuery[AsyncFileTable]

  def create(owner: User, kind: AsyncFileKind): Future[AsyncFile] =
    db.run(
      AsyncFileTableQuery returning AsyncFileTableQuery += AsyncFile(
        id = UUID.randomUUID(),
        userId = owner.id,
        creationDate = OffsetDateTime.now,
        kind = kind,
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

  def list(user: User, kind: Option[AsyncFileKind] = None): Future[List[AsyncFile]] =
    db.run(
      AsyncFileTableQuery
        .filter(_.userId === user.id)
        .filterOpt(kind) { case (table, kind) =>
          table.kind === kind
        }
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )
}
