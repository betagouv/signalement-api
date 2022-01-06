package models.auth

import play.api.libs.functional.syntax._
import play.api.libs.json.JsPath
import play.api.libs.json.JsonValidationError
import play.api.libs.json.Reads

case class PasswordChange(
    newPassword: String,
    oldPassword: String
)

object PasswordChange {
  implicit val userReads: Reads[PasswordChange] = (
    (JsPath \ "newPassword").read[String] and
      (JsPath \ "oldPassword").read[String]
  )(PasswordChange.apply _).filter(JsonValidationError("Passwords must not be equals"))(passwordChange =>
    passwordChange.newPassword != passwordChange.oldPassword
  )
}
