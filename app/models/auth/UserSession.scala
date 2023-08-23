package models.auth

import models.User
import play.api.libs.json.Json
import play.api.libs.json.OWrites

case class UserSession(token: String, user: User)

object UserSession {
  implicit val UserSessionFormat: OWrites[UserSession] = Json.writes[UserSession]
}
