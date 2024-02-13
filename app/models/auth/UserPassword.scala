package models.auth

import play.api.libs.json.JsValue
import play.api.libs.json.Reads

case class UserPassword(password: String) extends AnyVal

object UserPassword {
  implicit val UserPasswordReads: Reads[UserPassword] = (json: JsValue) =>
    (json \ "password").validate[String].map(UserPassword.apply)
}
