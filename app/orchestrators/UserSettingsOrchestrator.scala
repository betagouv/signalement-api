package orchestrators

import models.UserSettings
import play.api.libs.json.JsValue
import repositories.usersettings.UserSettingsRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext

class UserSettingsOrchestrator(userSettingsRepository: UserSettingsRepositoryInterface)(implicit
    ec: ExecutionContext
) {
  def saveReportsFilters(userId: UUID, filters: JsValue) =
    userSettingsRepository.createOrUpdate(UserSettings(userId, filters))

  def getReportsFilters(userId: UUID) =
    userSettingsRepository.get(userId)
}
