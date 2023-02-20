package repositories.usersettings

import models.UserSettings
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserSettingsRepository(val dbConfig: DatabaseConfig[JdbcProfile])(implicit val ec: ExecutionContext)
    extends UserSettingsRepositoryInterface {

  import repositories.PostgresProfile.api._
  import dbConfig._

  val table: TableQuery[UserSettingsTable] = TableQuery[UserSettingsTable]

  override def createOrUpdate(element: UserSettings): Future[UserSettings] =
    db.run(
      table
        .insertOrUpdate(element)
        .map(_ => element)
    )

  override def get(userId: UUID): Future[Option[UserSettings]] =
    db.run(table.filter(_.user_id === userId).result.headOption)
}
