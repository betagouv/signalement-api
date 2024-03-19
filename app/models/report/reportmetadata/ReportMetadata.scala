package models.report.reportmetadata

import models.company.Address
import models.report.Report
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.util.UUID

case class ReportMetadata(
    reportId: UUID,
    isMobileApp: Boolean,
    os: Option[Os],
    assignedUserId: Option[UUID]
)

object ReportMetadata {
  implicit val reportMetadataWrites: Writes[ReportMetadata] = Json.writes[ReportMetadata]

}

case class ReportWithMetadata(
    report: Report,
    metadata: Option[ReportMetadata]
) {
  def setAddress(companyAddress: Address) =
    this.copy(
      report = this.report.copy(companyAddress = companyAddress)
    )
}
object ReportWithMetadata {
  def fromTuple(tuple: (Report, Option[ReportMetadata])) = {
    val (report, metadata) = tuple
    ReportWithMetadata(report, metadata)
  }

}
