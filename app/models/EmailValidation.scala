package models

import models.EmailValidation.EmailValidationThreshold
import play.api.libs.json._
import utils.EmailAddress
import utils.QueryStringMapper

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

final case class EmailValidation(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    confirmationCode: String = f"${scala.util.Random.nextInt(1000000)}%06d",
    email: EmailAddress,
    attempts: Int = 0,
    lastAttempt: Option[OffsetDateTime] = None,
    lastValidationDate: Option[OffsetDateTime] = None
) {

  def isValid =
    this.lastValidationDate.isDefined && this.lastValidationDate.exists(_.isAfter(EmailValidationThreshold))

  def getValidationStatus =
    this.lastValidationDate match {
      case Some(date) if date.isAfter(EmailValidationThreshold) => EmailValidationStatus.Valid
      case Some(_)                                              => EmailValidationStatus.Expired
      case None                                                 => EmailValidationStatus.Invalid
    }

}

object EmailValidation {
  implicit val emailValidationformat: OFormat[EmailValidation] = Json.format[EmailValidation]

  def EmailValidationThreshold = OffsetDateTime.now().minusYears(1L)
}

final case class EmailValidationFilter(
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None,
    email: Option[EmailAddress] = None,
    validated: Option[Boolean] = None
)

object EmailValidationFilter {
  implicit val emailValidationFilterformat: OFormat[EmailValidationFilter] = Json.format[EmailValidationFilter]

  def fromQueryString(q: Map[String, Seq[String]]): Try[EmailValidationFilter] = Try {
    val mapper = new QueryStringMapper(q)
    EmailValidationFilter(
      email = mapper.string("email", trimmed = true).map(EmailAddress(_)),
      validated = mapper.boolean("validated")
    )
  }
}
