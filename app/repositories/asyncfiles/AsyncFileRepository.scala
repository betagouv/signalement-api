package repositories.asyncfiles

import enumeratum.SlickEnumSupport
import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import repositories.asyncfiles.AsyncFilesColumnType._
import slick.jdbc.JdbcProfile

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AsyncFileRepository @Inject(dbConfigProvider: DatabaseConfigProvider)(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[AsyncFilesTable, AsyncFile]
    with SlickEnumSupport
    with AsyncFileRepositoryInterface {

  override val profile: slick.jdbc.JdbcProfile = dbConfigProvider.get.profile
  override val dbConfig = dbConfigProvider.get[JdbcProfile]
  override val table: TableQuery[AsyncFilesTable] = AsyncFilesTable.table
  import dbConfig._

  override def update(uuid: UUID, filename: String, storageFilename: String): Future[Int] =
    db.run(
      table
        .filter(_.id === uuid)
        .map(f => (f.filename, f.storageFilename))
        .update((Some(filename), Some(storageFilename)))
    )

  override def list(user: User, kind: Option[AsyncFileKind] = None): Future[List[AsyncFile]] =
    db.run(
      table
        .filter(_.userId === user.id)
        .filterOpt(kind) { case (table, kind) =>
          table.kind === kind
        }
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )

  def deleteByUserId(userId: UUID): Future[Int] = db
    .run(
      table
        .filter(_.userId === userId)
        .delete
    )

}
