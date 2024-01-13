package repositories.tasklock

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import repositories.TypedCRUDRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait TaskRepositoryInterface extends TypedCRUDRepositoryInterface[TaskDetails, Int] {
  def acquireLock(id: Int): Future[Boolean]
  def releaseLock(id: Int): Future[Boolean]
}

class TaskRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[TaskDetailsTable, TaskDetails, Int]
    with TaskRepositoryInterface {

  import dbConfig._

  override val table = TaskDetailsTable.table

  def acquireLock(id: Int): Future[Boolean] =
    db.run(
      sql"""select pg_try_advisory_lock($id)""".as[Boolean].head
    )

  def releaseLock(id: Int): Future[Boolean] =
    db.run(
      sql"""select pg_advisory_unlock($id)""".as[Boolean].head
    )
}
