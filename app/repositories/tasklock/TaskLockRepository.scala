package repositories.tasklock

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import scala.concurrent.Future

trait TaskLockRepositoryInterface {
  def acquire(id: Int): Future[Boolean]
  def release(id: Int): Future[Boolean]
}

class TaskLockRepository(dbConfig: DatabaseConfig[JdbcProfile]) extends TaskLockRepositoryInterface {
  import dbConfig._

  def acquire(id: Int): Future[Boolean] =
    db.run(
      sql"""select pg_try_advisory_lock($id)""".as[Boolean].head
    )

  def release(id: Int): Future[Boolean] =
    db.run(
      sql"""select pg_advisory_unlock($id)""".as[Boolean].head
    )
}
