package models

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.util.UUID

case class UserReportsFilters(userId: UUID, name: String, reportsFilters: JsValue, defaultFilters: Boolean = false)

object UserReportsFilters {
  implicit val writes: Writes[UserReportsFilters] = Writes { userReportsFilters =>
    Json.obj(
      "name"    -> userReportsFilters.name,
      "filters" -> userReportsFilters.reportsFilters,
      "default" -> userReportsFilters.defaultFilters
    )
  }
}
