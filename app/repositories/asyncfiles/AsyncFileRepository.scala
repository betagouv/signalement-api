package repositories.asyncfiles

import enumeratum.SlickEnumSupport
import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile
import repositories.asyncfiles.AsyncFilesColumnType._
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class AsyncFileRepository @Inject() (dbConfigProvider: DatabaseConfigProvider) extends SlickEnumSupport {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._
  override val profile: slick.jdbc.JdbcProfile = dbConfigProvider.get.profile

  def create(owner: User, kind: AsyncFileKind): Future[AsyncFile] =
    db.run(
      AsyncFilesTable.Table returning AsyncFilesTable.Table += AsyncFile(
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
      AsyncFilesTable.Table
        .filter(_.id === uuid)
        .map(f => (f.filename, f.storageFilename))
        .update((Some(filename), Some(storageFilename)))
    )

  def list(user: User, kind: Option[AsyncFileKind] = None): Future[List[AsyncFile]] =
    db.run(
      AsyncFilesTable.Table
        .filter(_.userId === user.id)
        .filterOpt(kind) { case (table, kind) =>
          table.kind === kind
        }
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )

  def delete(userId: UUID): Future[Int] = db
    .run(
      AsyncFilesTable.Table
        .filter(_.userId === userId)
        .delete
    )

}
