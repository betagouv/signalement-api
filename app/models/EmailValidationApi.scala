package models

import enumeratum.EnumEntry
import enumeratum.PlayEnum
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
    validationStatus: EmailValidationStatus
)

object EmailValidationApi {
  implicit val EmailValidationApiFormat: OFormat[EmailValidationApi] = Json.format[EmailValidationApi]

  def fromEmailValidation(emailValidation: EmailValidation): EmailValidationApi =
    emailValidation
      .into[EmailValidationApi]
      .withFieldComputed(
        _.validationStatus,
        _.getValidationStatus
      )
      .transform
}

sealed trait EmailValidationStatus extends EnumEntry

object EmailValidationStatus extends PlayEnum[EmailValidationStatus] {

  case object Valid   extends EmailValidationStatus
  case object Expired extends EmailValidationStatus
  case object Invalid extends EmailValidationStatus

  override def values: IndexedSeq[EmailValidationStatus] = findValues
}
