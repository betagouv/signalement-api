package models

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.json._
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

final case class EmailValidation(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime = OffsetDateTime.now,
    confirmationCode: String = f"${scala.util.Random.nextInt(1000000)}%06d",
    email: EmailAddress,
    attempts: Int = 0,
    lastAttempt: Option[OffsetDateTime] = None,
    lastValidationDate: Option[OffsetDateTime] = None
) extends Identity {}

object EmailValidation {
  implicit val emailValidationformat: OFormat[EmailValidation] = Json.format[EmailValidation]
}
