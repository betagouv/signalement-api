package models.company

import io.scalaland.chimney.dsl.TransformerOps
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.CountryCode

case class AddressApi(
    number: Option[String] = None,
    street: Option[String] = None,
    addressSupplement: Option[String] = None,
    postalCode: Option[String] = None,
    city: Option[String] = None,
    country: Option[CountryCode] = None
)

object AddressApi {
  implicit val addressFormat: OFormat[AddressApi] = Json.format[AddressApi]

  def fromAdress(address: Address) =
    address.into[AddressApi].withFieldConst(_.country, address.country.map(_.code)).transform
}
