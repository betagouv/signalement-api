package models.dataeconomie

import models.report.ReportStatus
import play.api.libs.json.Format
import play.api.libs.json.Json
import utils.Country

import java.time.OffsetDateTime
import java.util.UUID

case class ReportDataEconomie(
    id: UUID,
    category: String,
    subcategories: List[String],
    companyId: Option[UUID],
    companyNumber: Option[String] = None,
    companyStreet: Option[String] = None,
    companyAddressSupplement: Option[String] = None,
    companyCity: Option[String] = None,
    companyCountry: Option[Country] = None,
    companyPostalCode: Option[String],
    creationDate: OffsetDateTime,
    contactAgreement: Boolean,
    status: ReportStatus,
    forwardToReponseConso: Boolean,
    vendor: Option[String],
    tags: List[String],
    activityCode: Option[String]
)

object ReportDataEconomie {
  implicit val ReportDataFormat: Format[ReportDataEconomie] = Json.format[ReportDataEconomie]
}
