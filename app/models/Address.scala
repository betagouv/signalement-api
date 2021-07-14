package models

import play.api.libs.json.{Json, OFormat}
import utils.Country

case class Address(
  number: Option[String] = None,
  street: Option[String] = None,
  addressSupplement: Option[String] = None,
  postalCode: Option[String] = None,
  city: Option[String] = None,
  country: Option[Country] = None,
) {

  def isDefined: Boolean = (for {
    _ <- number
    _ <- street
    _ <- addressSupplement
    _ <- postalCode
    _ <- city
    _ <- country
  } yield true).getOrElse(false)

  def nonEmpty: Boolean = !isDefined

  private[this] def fullStreet: String = (number.getOrElse(" ") + street.getOrElse("")).trim()

  private[this] def fullCity: String = (postalCode.getOrElse(" ") + city.getOrElse("")).trim()

  def toArray: Seq[String] = Seq(
    fullStreet,
    addressSupplement.getOrElse(""),
    fullCity,
    country.map(_.name).getOrElse("")
  ).filter(_ != "")

  override def toString: String = toArray.mkString(" - ")
}

object Address {
  implicit val addressFormat: OFormat[Address] = Json.format[Address]
}