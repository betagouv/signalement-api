package models.auth

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class UserCredentials(
    login: String,
    password: String
)

object UserCredentials {
  implicit val userLoginFormat: OFormat[UserCredentials] = Json.format[UserCredentials]
}
