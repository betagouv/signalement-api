package repositories.usersettings

import models.UserReportsFilters
import play.api.libs.json.JsValue
import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID

class UserReportsFiltersTable(tag: Tag) extends Table[UserReportsFilters](tag, "user_reports_filters") {

  def user_id         = column[UUID]("user_id")
  def name            = column[String]("name")
  def reports_filters = column[JsValue]("filters")
  def default_filters = column[Boolean]("default_filters")
  def pk              = primaryKey("pk_user_id_name", (user_id, name))

  override def * : ProvenShape[UserReportsFilters] =
    (
      user_id,
      name,
      reports_filters,
      default_filters
    ) <> ((UserReportsFilters.apply _).tupled, UserReportsFilters.unapply)
}
