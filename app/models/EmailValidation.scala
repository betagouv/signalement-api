package models

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.json._
import utils.EmailAddress

case class EmailValidation(
  id: UUID = UUID.randomUUID(),
  creationDate: OffsetDateTime = OffsetDateTime.now,
  email: EmailAddress,
  lastValidationDate: Option[OffsetDateTime] = None,
) extends Identity {
}

object EmailValidation {
  implicit val format: OFormat[EmailValidation] = Json.format[EmailValidation]
}

case class EmailValidationCreate(
  email: EmailAddress,
  lastValidationDate: Option[OffsetDateTime] = None,
) {
  def toEntity(): EmailValidation = {
    EmailValidation(
      email = email,
      lastValidationDate = lastValidationDate,
    )
  }
}

object EmailValidationCreate {
  implicit val format = Json.format[EmailValidationCreate]
}

