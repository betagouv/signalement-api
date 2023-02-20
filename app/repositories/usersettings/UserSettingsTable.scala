package repositories.usersettings

import models.UserSettings
import play.api.libs.json.JsValue
import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID

class UserSettingsTable(tag: Tag) extends Table[UserSettings](tag, "user_settings") {

  def user_id = column[UUID]("user_id", O.PrimaryKey)
  def reports_filters = column[JsValue]("reports_filters")

  override def * : ProvenShape[UserSettings] =
    (user_id, reports_filters) <> ((UserSettings.apply _).tupled, UserSettings.unapply)
}
