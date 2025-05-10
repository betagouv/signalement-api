package models.email

import play.api.libs.json.{Json, OFormat}
import utils.EmailAddress

import java.util.Locale

case class ValidateEmail(email: EmailAddress, lang: Option[Locale])

object ValidateEmail {
  implicit val EmailBodyFormat: OFormat[ValidateEmail] = Json.format[ValidateEmail]
}

case class ValidateEmailCode(email: EmailAddress, confirmationCode: String, consentToUseData: Option[Boolean])

object ValidateEmailCode {
  implicit val EmailValidationBodyFormat: OFormat[ValidateEmailCode] = Json.format[ValidateEmailCode]
}
