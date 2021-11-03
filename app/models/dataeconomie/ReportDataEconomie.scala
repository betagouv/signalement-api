package models.dataeconomie

import play.api.libs.json.Format
import play.api.libs.json.Json
import utils.Constants.ReportStatus.ReportStatusValue

import java.time.OffsetDateTime
import java.util.UUID

case class ReportDataEconomie(
    id: UUID,
    category: String,
    subcategories: List[String],
    companyId: Option[UUID],
    postalCode: Option[String],
    creationDate: OffsetDateTime,
    contactAgreement: Boolean,
    status: ReportStatusValue,
    forwardToReponseConso: Boolean,
    vendor: Option[String],
    tags: List[String]
)

object ReportDataEconomie {
  implicit val ReportStatusValueWrites = ReportStatusValue.ReportStatusValueWrites
  implicit val ReportDataFormat: Format[ReportDataEconomie] = Json.format[ReportDataEconomie]
}
