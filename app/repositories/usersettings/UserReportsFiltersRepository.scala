package repositories.usersettings

import models.UserReportsFilters
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserReportsFiltersRepository(val dbConfig: DatabaseConfig[JdbcProfile])(implicit val ec: ExecutionContext)
    extends UserReportsFiltersRepositoryInterface {

  import repositories.PostgresProfile.api._
  import dbConfig._

  val table: TableQuery[UserReportsFiltersTable] = TableQuery[UserReportsFiltersTable]

  override def createOrUpdate(element: UserReportsFilters): Future[UserReportsFilters] =
    db.run(
      table
        .insertOrUpdate(element)
        .map(_ => element)
    )

  override def get(userId: UUID, name: String): Future[Option[UserReportsFilters]] =
    db.run(table.filter(_.user_id === userId).filter(_.name === name).result.headOption)

  override def list(userId: UUID): Future[List[UserReportsFilters]] =
    db.run(table.filter(_.user_id === userId).to[List].result)

  override def delete(userId: UUID, name: String): Future[Unit] =
    db.run(table.filter(_.user_id === userId).filter(_.name === name).delete).map(_ => ())

  override def rename(userId: UUID, oldName: String, newName: String): Future[Unit] =
    db.run(table.filter(_.user_id === userId).filter(_.name === oldName).map(_.name).update(newName)).map(_ => ())

  override def setAsDefault(userId: UUID, name: String): Future[Unit] = {
    val removeDefault =
      table.filter(_.user_id === userId).filter(_.default_filters === true).map(_.default_filters).update(false)
    val setDefault = table.filter(_.user_id === userId).filter(_.name === name).map(_.default_filters).update(true)
    db.run(DBIO.seq(removeDefault, setDefault))
  }

  override def unsetDefault(userId: UUID, name: String): Future[Unit] =
    db.run(table.filter(_.user_id === userId).filter(_.name === name).map(_.default_filters).update(false)).map(_ => ())
}
