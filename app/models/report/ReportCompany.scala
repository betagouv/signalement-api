package models.report

import models.company.Address
import models.company.Company
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

case class ReportCompany(
    name: String,
    commercialName: Option[String],
    establishmentCommercialName: Option[String],
    brand: Option[String],
    address: Address,
    siret: SIRET,
    activityCode: Option[String],
    isHeadOffice: Boolean,
    isOpen: Boolean,
    isPublic: Boolean
)

object ReportCompany {
  implicit val format: OFormat[ReportCompany] = Json.format[ReportCompany]

  implicit class ReportCompanyOps(reportCompany: ReportCompany) {

    def toCompany = Company(
      siret = reportCompany.siret,
      name = reportCompany.name,
      address = reportCompany.address,
      activityCode = reportCompany.activityCode,
      isHeadOffice = reportCompany.isHeadOffice,
      isOpen = reportCompany.isOpen,
      isPublic = reportCompany.isPublic,
      brand = reportCompany.brand,
      commercialName = reportCompany.commercialName,
      establishmentCommercialName = reportCompany.establishmentCommercialName
    )
  }

}
