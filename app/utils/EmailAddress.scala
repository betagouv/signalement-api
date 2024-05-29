package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class EmailAddress private (value: String) extends AnyVal {
  override def toString = value

  // Gmail allows to put '.' in the email as a separator. The email is still the same.
  // Gmail also allows the '+' trick. We want to bleck all these things
  def isEquivalentTo(baseEmailAddress: String): Boolean = {
    val Array(address, domain)         = value.split("@")
    val Array(baseAddress, baseDomain) = baseEmailAddress.split("@")
    if (domain == "gmail.com" && baseDomain == "gmail.com") {
      address.split('+').head.filter(c => c != '.') == baseAddress.split('+').head.filter(c => c != '.')
    } else {
      baseEmailAddress == value
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
