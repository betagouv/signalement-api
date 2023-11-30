package tasks.company

import io.scalaland.chimney.dsl.TransformerOps
import models.company.AddressApi
import models.company.Company
import models.website.Website
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

import java.time.OffsetDateTime

case class CompanySearchResultApi(
    siret: SIRET,
    name: Option[String],
    brand: Option[String],
    isHeadOffice: Boolean,
    address: AddressApi,
    activityCode: Option[String],
    activityLabel: Option[String],
    isMarketPlace: Boolean = false,
    isOpen: Boolean,
    isPublic: Boolean,
    lastUpdated: Option[OffsetDateTime] = None
)

object CompanySearchResultApi {
  implicit val format: OFormat[CompanySearchResultApi] = Json.format[CompanySearchResultApi]

  def fromCompany(company: Company, website: Website) =
    company
      .into[CompanySearchResultApi]
      .withFieldConst(_.isMarketPlace, website.isMarketplace)
      .withFieldConst(_.activityLabel, None)
      .withFieldComputed(_.address, c => AddressApi.fromAdress(c.address.toFilteredAddress(c.isPublic)))
      .enableDefaultValues
      .transform
}
