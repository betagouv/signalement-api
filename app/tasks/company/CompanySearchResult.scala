package tasks.company

import io.scalaland.chimney.dsl._
import models.company.Address
import models.company.Company
import models.company.CompanyCreation
import models.website.Website
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

import java.time.OffsetDateTime

case class CompanySearchResult(
    siret: SIRET,
    name: Option[String],
    commercialName: Option[String],
    establishmentCommercialName: Option[String],
    brand: Option[String],
    isHeadOffice: Boolean,
    address: Address,
    activityCode: Option[String],
    activityLabel: Option[String],
    isMarketPlace: Boolean = false,
    isOpen: Boolean,
    isPublic: Boolean,
    lastUpdated: Option[OffsetDateTime] = None
) {
  def toCreation =
    CompanyCreation(
      siret = siret,
      name = name.getOrElse(""),
      address = address,
      activityCode = activityCode,
      isHeadOffice = Some(isHeadOffice),
      isOpen = Some(isOpen),
      isPublic = Some(isPublic),
      brand = brand,
      commercialName = commercialName,
      establishmentCommercialName = establishmentCommercialName
    )
}

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]

  def fromCompany(company: Company, website: Website) =
    company
      .into[CompanySearchResult]
      .withFieldConst(_.isMarketPlace, website.isMarketplace)
      .withFieldConst(_.activityLabel, None)
      .withFieldComputed(_.address, c => c.address.toFilteredAddress(c.isPublic))
      .enableDefaultValues
      .transform
}
