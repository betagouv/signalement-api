package models

import org.specs2.mutable.Specification
import models.DetailInputValue._

class AddressSpec extends Specification {

  "AddressSpec" should {

    "isDefined" in {
      Address(
        number = None,
        street = None,
        addressSupplement = None,
        postalCode = None,
        city = None,
      ).isDefined === false
    }

    "isDefined" in {
      Address(
        number = Some("1"),
        street = None,
        addressSupplement = None,
        postalCode = None,
        city = None,
      ).isDefined === true
    }

    "isDefined" in {
      Address(
        number = None,
        street = None,
        addressSupplement = None,
        postalCode = Some("90100"),
        city = None,
      ).isDefined === false
    }

    "isDefined" in {
      Address(
        number = None,
        street = None,
        addressSupplement = Some("test"),
        postalCode = None,
        city = None,
      ).isDefined === false
    }

    "toString" in {
      Address(
        number = Some("13"),
        street = Some("AVENUE FELIX FAURE"),
        addressSupplement = Some("1 RUE ARDOINO"),
        postalCode = Some("06500"),
        city = Some("MENTON"),
      ).toString must equalTo("13 AVENUE FELIX FAURE - 1 RUE ARDOINO - 06500 MENTON")

      Address(
        number = None,
        street = None,
        addressSupplement = Some("1 RUE ARDOINO"),
        postalCode = Some("06500"),
        city = Some("MENTON"),
      ).toString must equalTo("1 RUE ARDOINO - 06500 MENTON")

      Address(
        number = Some("13"),
        street = Some("AVENUE FELIX FAURE"),
        addressSupplement = None,
        postalCode = None,
        city = Some("MENTON"),
      ).toString must equalTo("13 AVENUE FELIX FAURE - MENTON")

      Address(
        number = None,
        street = None,
        addressSupplement = None,
        postalCode = None,
        city = None,
      ).toString must equalTo("")
    }
  }
}
