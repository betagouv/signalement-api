package models.auth

import play.api.libs.json.JsValue
import play.api.libs.json.Reads

case class UserLogin(login: String) extends AnyVal

object UserLogin {
  implicit val UserLoginReads: Reads[UserLogin] = (json: JsValue) =>
    (json \ "login").validate[String].map(UserLogin.apply)
}
