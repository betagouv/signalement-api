package models.email

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

case class ValidateEmail(email: EmailAddress)

object ValidateEmail {
  implicit val EmailBodyFormat: OFormat[ValidateEmail] = Json.format[ValidateEmail]
}
