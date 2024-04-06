package models

import io.scalaland.chimney.dsl.TransformationOps
import play.api.libs.json._
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

final case class EmailValidationApi(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    confirmationCode: String = f"${scala.util.Random.nextInt(1000000)}%06d",
    email: EmailAddress,
    attempts: Int = 0,
    lastAttempt: Option[OffsetDateTime] = None,
    lastValidationDate: Option[OffsetDateTime] = None,
    isValid: Boolean
)

object EmailValidationApi {
  implicit val EmailValidationApiFormat: OFormat[EmailValidationApi] = Json.format[EmailValidationApi]

  def fromEmailValidation(emailValidation: EmailValidation): EmailValidationApi =
    emailValidation
      .into[EmailValidationApi]
      .withFieldComputed(
        _.isValid,
        _.isValid
      )
      .transform
}
