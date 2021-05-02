package models

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.json._
import utils.EmailAddress

final case class EmailValidation(
  id: UUID = UUID.randomUUID(),
  creationDate: OffsetDateTime = OffsetDateTime.now,
  confirmationCode: String = f"${scala.util.Random.nextInt(1000000)}%06d",
  email: EmailAddress,
  attempts: Int = 0,
  lastAttempt: Option[OffsetDateTime] = None,
  lastValidationDate: Option[OffsetDateTime] = None,
) extends Identity {
}

object EmailValidation {
  implicit val emailValidationformat: OFormat[EmailValidation] = Json.format[EmailValidation]
}

final case class EmailValidationCreate(
  email: EmailAddress,
  lastValidationDate: Option[OffsetDateTime] = None,
) {
  def toEntity: EmailValidation = {
    EmailValidation(
      email = email,
      lastValidationDate = lastValidationDate,
    )
  }
}
