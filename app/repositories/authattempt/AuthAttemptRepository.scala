package repositories.authattempt

import models.PaginatedResult
import models.auth.AuthAttempt
import models.auth.AuthAttemptFilter
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class AuthAttemptRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[AuthAttemptTable, AuthAttempt]
    with AuthAttemptRepositoryInterface {

  override val table: TableQuery[AuthAttemptTable] = AuthAttemptTable.table
  import dbConfig._
  val logger: Logger = Logger(this.getClass)

  override def countAuthAttempts(login: String, delay: Duration): Future[Int] = db
    .run(
      table
        .filter(_.login === login)
        .filter(_.timestamp >= OffsetDateTime.now().minusMinutes(delay.toMinutes))
        .length
        .result
    )

  override def countAuthAttempts(filter: AuthAttemptFilter): Future[Int] = db.run(
    table
      .filterOpt(filter.isSuccess) { case (table, isSuccess) =>
        table.isSuccess.map(_ === isSuccess)
      }
      .filterOpt(filter.start) { case (table, start) =>
        table.timestamp >= start
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.timestamp <= end
      }
      .length
      .result
  )

  override def listAuthAttempts(
      login: Option[String],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[AuthAttempt]] =
    table
      .filterOpt(login) { case (table, login) =>
        table.login like s"%${login}%"
      }
      .withPagination(db)(
        maybeOffset = offset,
        maybeLimit = limit,
        maybePreliminaryAction = None
      )
      .sortBy(_.timestamp.desc)

}
