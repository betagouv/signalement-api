package models.dataeconomie

import models.ReportStatus2
import play.api.libs.json.Format
import play.api.libs.json.Json

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
    status: ReportStatus2,
    forwardToReponseConso: Boolean,
    vendor: Option[String],
    tags: List[String]
)

object ReportDataEconomie {
  implicit val ReportDataFormat: Format[ReportDataEconomie] = Json.format[ReportDataEconomie]
}
