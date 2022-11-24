package tasks.company

import io.scalaland.chimney.dsl.TransformerOps
import models.company.Address
import models.company.Company
import models.website.Website
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

import java.time.OffsetDateTime

case class CompanySearchResult(
    siret: SIRET,
    name: Option[String],
    brand: Option[String],
    isHeadOffice: Boolean,
    address: Address,
    activityCode: Option[String],
    activityLabel: Option[String],
    isMarketPlace: Boolean = false,
    isOpen: Boolean,
    isPublic: Boolean,
    lastUpdated: Option[OffsetDateTime] = None
)

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]

  def fromCompany(company: Company, website: Website) =
    company
      .into[CompanySearchResult]
      .withFieldConst(_.isMarketPlace, website.isMarketplace)
      .withFieldConst(_.activityLabel, None)
      .withFieldConst(_.brand, None)
      .withFieldConst(_.address, company.address.toFilteredAddress(company.isPublic))
      .transform
}
