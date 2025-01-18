package utils

import cats.implicits.catsSyntaxEitherId
import controllers.error.AppError
import controllers.error.AppError.InvalidEmail
import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class EmailAddress private (value: String) extends AnyVal {
  override def toString = value

  def split: Either[InvalidEmail, EmailAddressSplitted] =
    value.split("@") match {
      case Array(address, domain) =>
        EmailAddressSplitted(value, address.split('+').head.filter(c => c != '.'), domain).asRight
      // In case email has several @ or none
      case _ => AppError.InvalidEmail(value).asLeft
    }

}

// Gmail allows to put '.' in the email as a separator. The email is still the same.
// Gmail also allows the '+' trick. We want to block all these things
case class EmailAddressSplitted(rawValue: String, rootAddress: String, domain: String) {

  def isEquivalentTo(other: String): Boolean =
    if (other.isBlank) false // Because of RGPD Deletion, emails can be empty
    else {
      other.split("@") match {
        case Array(otherAddress, otherDomain) if domain == "gmail.com" && otherDomain == "gmail.com" =>
          rootAddress == otherAddress.split('+').headOption.map(_.filter(c => c != '.')).getOrElse("")
        case _ =>
          rawValue == other
      }
    }
}

object EmailAddress {

  val EmptyEmailAddress: EmailAddress = EmailAddress("")

  implicit class EmailAddressOps(emailAddress: EmailAddress) {
    implicit def isEmpty: Boolean  = emailAddress == EmptyEmailAddress
    implicit def nonEmpty: Boolean = emailAddress != EmptyEmailAddress
  }

  def apply(value: String) = new EmailAddress(value.trim.toLowerCase)
  implicit val EmailColumnType: JdbcType[EmailAddress] with BaseTypedType[EmailAddress] =
    MappedColumnType.base[EmailAddress, String](
      _.value,
      EmailAddress(_)
    )
  implicit val emailWrites: Writes[EmailAddress] = Json.valueWrites[EmailAddress]
  implicit val emailReads: Reads[EmailAddress]   = Reads.StringReads.map(EmailAddress(_)) // To use the apply method

}
