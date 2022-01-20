package models.email

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

case class ValidateEmailCode(email: EmailAddress, confirmationCode: String)

object ValidateEmailCode {
  implicit val EmailValidationBodyFormat: OFormat[ValidateEmailCode] = Json.format[ValidateEmailCode]
}
