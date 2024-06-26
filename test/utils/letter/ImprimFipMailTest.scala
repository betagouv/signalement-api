package utils.letter

import models.company.Address
import models.company.Company
import org.specs2.mutable.Specification
import utils.SIRET
import utils.letter.ImprimFipMail.AddressImprimFitOps
import utils.letter.ImprimFipMail.CompanyImprimFitOps
import utils.letter.ImprimFipMail.RecipientLineLength

class ImprimFipMailTest extends Specification {

  "CompanyImprimFitOps" should {

    "return recipient lines with company name in uppercase" in {
      val address = Address(Some("123"), Some("Main St"), postalCode = Some("12345"), city = Some("Sample City"))
      val company = Company(
        siret = SIRET("12345678901234"),
        name = "Sample Company",
        address = address,
        activityCode = None,
        isHeadOffice = true,
        isOpen = true,
        isPublic = false,
        brand = None,
        commercialName = None,
        establishmentCommercialName = None
      )
      val ops      = new CompanyImprimFitOps(company)
      val expected = Seq("SAMPLE COMPANY", "123 MAIN ST", "12345 SAMPLE CITY")
      ops.toRecipientLines must beEqualTo(expected)
    }

    "handle company name and brand with multiple lines" in {
      val address = Address(Some("123"), Some("Main St"), postalCode = Some("12345"), city = Some("Sample City"))
      val company = Company(
        siret = SIRET("12345678901234"),
        name = "A very long company name that exceeds the limit of thirty-five characters",
        address = address,
        activityCode = None,
        isHeadOffice = true,
        isOpen = true,
        isPublic = false,
        brand = Some("Brand Name"),
        commercialName = None,
        establishmentCommercialName = None
      )
      val ops = new CompanyImprimFitOps(company)
      val expected = Seq(
        "A VERY LONG COMPANY NAME THAT",
        "EXCEEDS THE LIMIT OF THIRTY-FIVE",
        "CHARACTERS",
        "BRAND NAME",
        "123 MAIN ST",
        "12345 SAMPLE CITY"
      )
      ops.toRecipientLines must beEqualTo(expected)
    }

    "handle empty company brand" in {
      val address = Address(Some("123"), Some("Main St"), postalCode = Some("12345"), city = Some("Sample City"))
      val company = Company(
        siret = SIRET("12345678901234"),
        name = "Sample Company",
        address = address,
        activityCode = None,
        isHeadOffice = true,
        isOpen = true,
        isPublic = false,
        brand = Some(""),
        commercialName = None,
        establishmentCommercialName = None
      )
      val ops      = new CompanyImprimFitOps(company)
      val expected = Seq("SAMPLE COMPANY", "123 MAIN ST", "12345 SAMPLE CITY")
      ops.toRecipientLines must beEqualTo(expected)
    }

    "handle company with both name and brand filling the maximum lines" in {
      val address = Address(Some("123"), Some("Main St"), postalCode = Some("12345"), city = Some("Sample City"))
      val company = Company(
        siret = SIRET("12345678901234"),
        name = "Company Name Line 1",
        address = address,
        activityCode = None,
        isHeadOffice = true,
        isOpen = true,
        isPublic = false,
        brand = Some("Brand Line 1"),
        commercialName = None,
        establishmentCommercialName = None
      )
      val expected = Seq("COMPANY NAME LINE 1", "BRAND LINE 1", "123 MAIN ST", "12345 SAMPLE CITY")
      company.toRecipientLines must beEqualTo(expected)
    }

    "handle company with a long address" in {
      val address = Address(
        Some("123"),
        Some(
          "A very long street name that will be split into multiple lines because it exceeds the limit of thirty-five characters"
        ),
        postalCode = Some("12345"),
        city = Some("Sample City")
      )
      val company = Company(
        siret = SIRET("12345678901234"),
        name = "Sample Company",
        address = address,
        activityCode = None,
        isHeadOffice = true,
        isOpen = true,
        isPublic = false,
        brand = None,
        commercialName = None,
        establishmentCommercialName = None
      )
      val ops = new CompanyImprimFitOps(company)
      val expected = Seq(
        "SAMPLE COMPANY",
        "123 A VERY LONG STREET NAME THAT",
        "WILL BE SPLIT INTO MULTIPLE LINES",
        "BECAUSE IT EXCEEDS THE LIMIT OF",
        "THIRTY-FIVE CHARACTERS",
        "12345 SAMPLE CITY"
      )
      ops.toRecipientLines must beEqualTo(expected)
    }

  }

  "AddressImprimFitOps" should {

    "return the correct fullStreet" in {
      val address = Address(Some("123"), Some("Main St"))
      val ops     = new AddressImprimFitOps(address)
      ops.fullStreet must beEqualTo("123 Main St")
    }

    "return the correct fullCity" in {
      val address = Address(postalCode = Some("12345"), city = Some("Sample City"))
      val ops     = new AddressImprimFitOps(address)
      ops.cityRecipientLine must beEqualTo("12345 Sample City")
    }

    "return the correct recipient lines" in {
      val address  = Address(Some("123"), Some("Main St"), Some("Apt 4"), Some("12345"), Some("Sample City"))
      val ops      = new AddressImprimFitOps(address)
      val expected = Seq("123 MAIN ST", "APT 4")
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "handle missing address fields correctly" in {
      val address  = Address(None, None, None, None, None)
      val ops      = new AddressImprimFitOps(address)
      val expected = Seq.empty[String]
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "split long street names into multiple lines" in {
      val address  = Address(Some("123"), Some("Very Long Street Name That Exceeds The Limit"))
      val ops      = new AddressImprimFitOps(address)
      val expected = Seq("123 VERY LONG STREET NAME THAT", "EXCEEDS THE LIMIT")
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "truncate city names that exceed the maximum length" in {
      val address =
        Address(postalCode = Some("12345"), city = Some("A Very Long City Name That Exceeds The Maximum Size"))
      val ops = new AddressImprimFitOps(address)
      ops.cityRecipientLine must beEqualTo("12345 A Very Long City Name That Exc")
      ops.cityRecipientLine.length must beLessThan(RecipientLineLength + 1)
    }

    "handle fullStreetLines with single line" in {
      val address  = Address(Some("123"), Some("Main St"), postalCode = Some("12345"), city = Some("Sample City"))
      val ops      = new AddressImprimFitOps(address)
      val expected = Seq("123 MAIN ST")
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "handle fullStreetLines with exactly one full line" in {
      val address = Address(
        Some("123"),
        Some("A street name that is exactly thirty-five characters long"),
        postalCode = Some("12345"),
        city = Some("Sample City")
      )
      val ops      = new AddressImprimFitOps(address)
      val expected = Seq("123 A STREET NAME THAT IS EXACTLY", "THIRTY-FIVE CHARACTERS LONG")
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "handle fullStreetLines with multiple full lines" in {
      val address = Address(
        number = Some("123"),
        street = Some(
          "A very long street name that will be split into multiple lines because it exceeds the limit of thirty-five characters per line"
        ),
        postalCode = Some("12345"),
        city = Some("Sample City")
      )
      val ops = new AddressImprimFitOps(address)
      val expected = Seq(
        "123 A VERY LONG STREET NAME THAT",
        "WILL BE SPLIT INTO MULTIPLE LINES",
        "BECAUSE IT EXCEEDS THE LIMIT OF",
        "THIRTY-FIVE CHARACTERS PER LINE"
      )
      ops.streetRecipientLines must beEqualTo(expected)
    }

    "handle fullStreetLines with more lines than AdressLineCount" in {
      val address = Address(
        number = Some("123"),
        street = Some(
          "A long street name that will be split into more lines"
        ),
        addressSupplement = Some("A very very long addressSupplement name"),
        postalCode = Some("12345"),
        city = Some("Sample City")
      )
      val ops = new AddressImprimFitOps(address)
      val expected = Seq(
        "123 A LONG STREET NAME THAT WILL BE",
        "SPLIT INTO MORE LINES",
        "A VERY VERY LONG ADDRESSSUPPLEMENT",
        "NAME"
      )
      ops.streetRecipientLines must beEqualTo(expected)
    }

  }
}
