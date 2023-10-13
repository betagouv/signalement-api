package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class EmailAddress private (value: String) extends AnyVal {
  override def toString = value
}

object EmailAddress {

  val EmptyEmailAddress: EmailAddress = EmailAddress("")

  implicit class EmailAddressOps(emailAddress: EmailAddress) {
    implicit def isEmpty: Boolean = emailAddress == EmptyEmailAddress
    implicit def nonEmpty: Boolean = emailAddress != EmptyEmailAddress
  }

  def apply(value: String) = new EmailAddress(value.trim.toLowerCase)
  implicit val EmailColumnType: JdbcType[EmailAddress] with BaseTypedType[EmailAddress] =
    MappedColumnType.base[EmailAddress, String](
      _.value,
      EmailAddress(_)
    )
  implicit val emailWrites: Writes[EmailAddress] = Json.valueWrites[EmailAddress]
  implicit val emailReads: Reads[EmailAddress] = Reads.StringReads.map(EmailAddress(_)) // To use the apply method

}
