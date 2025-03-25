package models.event

import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

case class EventUser(id: UUID, firstName: String, lastName: String, role: UserRole)

object EventUser {
  implicit val EventUserFormat: OFormat[EventUser] = Json.format[EventUser]
}
