package repositories.usersettings

import models.UserSettings

import java.util.UUID
import scala.concurrent.Future

trait UserSettingsRepositoryInterface {
  def createOrUpdate(element: UserSettings): Future[UserSettings]

  def get(userId: UUID): Future[Option[UserSettings]]
}
