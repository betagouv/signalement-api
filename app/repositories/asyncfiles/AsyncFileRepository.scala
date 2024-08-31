package repositories.asyncfiles

import models._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import repositories.asyncfiles.AsyncFilesColumnType._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.ResultSetConcurrency
import slick.jdbc.ResultSetType

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AsyncFileRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[AsyncFilesTable, AsyncFile]
    with AsyncFileRepositoryInterface {

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
        .filter(_.creationDate >= OffsetDateTime.now().minusDays(1))
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )

  def streamOldReportExports: Source[AsyncFile, NotUsed] = Source
    .fromPublisher(
      db.stream(
        table
          .filter(_.creationDate <= OffsetDateTime.now().minusDays(10))
          .result
          .withStatementParameters(
            rsType = ResultSetType.ForwardOnly,
            rsConcurrency = ResultSetConcurrency.ReadOnly,
            fetchSize = 10000
          )
          .transactionally
      )
    )
    .log("user")

  def deleteByUserId(userId: UUID): Future[Int] = db
    .run(
      table
        .filter(_.userId === userId)
        .delete
    )

}
