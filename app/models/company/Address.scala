package models.company

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.Country

case class Address(
    number: Option[String] = None,
    street: Option[String] = None,
    addressSupplement: Option[String] = None,
    postalCode: Option[String] = None,
    city: Option[String] = None,
    country: Option[Country] = None
) {

  def toFilteredAddress(isPublic: Boolean): Address =
    this.copy(
      number = this.number
        .filter(_ => isPublic),
      street = this.street
        .filter(_ => isPublic),
      addressSupplement = this.addressSupplement
        .filter(_ => isPublic)
    )

  def isDefined: Boolean = List(
    number,
    street,
    addressSupplement,
    postalCode,
    city,
    country
  ).exists(_.isDefined)

  def nonEmpty: Boolean = !isDefined

  private[this] def fullStreet: String = (number.getOrElse("") + " " + street.getOrElse("")).trim()

  private[this] def fullCity: String = (postalCode.getOrElse("") + " " + city.getOrElse("")).trim()

  def toArray: Seq[String] = Seq(
    fullStreet,
    addressSupplement.getOrElse(""),
    fullCity,
    country.map(_.name).getOrElse("")
  ).filter(_ != "")

  def toPrivateArray: Seq[String] = Seq(
    fullCity,
    country.map(_.name).getOrElse("")
  ).filter(_ != "")

  override def toString: String = toArray.mkString(" - ")
}

object Address {
  implicit val addressFormat: OFormat[Address] = Json.format[Address]

  def merge(base: Option[Address], alternative: Option[Address]): Address = {
    val a = base.getOrElse(Address())
    val b = alternative.getOrElse(Address())
    Address(
      number = a.number.orElse(b.number),
      street = a.street.orElse(b.street),
      addressSupplement = a.addressSupplement.orElse(b.addressSupplement),
      postalCode = a.postalCode.orElse(b.postalCode),
      city = a.city.orElse(b.city),
      country = a.country.orElse(b.country)
    )
  }

}
