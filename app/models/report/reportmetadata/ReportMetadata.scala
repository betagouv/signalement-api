package models.report.reportmetadata

import models.company.Address
import models.report.Report
import play.api.libs.json.Json
import play.api.libs.json.Writes
import repositories.bookmark.Bookmark
import repositories.subcategorylabel.SubcategoryLabel

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

case class ReportWithMetadataAndBookmark(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    subcategoryLabel: Option[SubcategoryLabel]
) {
  def setAddress(companyAddress: Address) =
    this.copy(
      report = this.report.copy(companyAddress = companyAddress)
    )
}
object ReportWithMetadataAndBookmark {
  def from(report: Report, metadata: Option[ReportMetadata], bookmark: Option[Bookmark], subcategoryLabel: Option[SubcategoryLabel] = None): ReportWithMetadataAndBookmark = {
    ReportWithMetadataAndBookmark(report, metadata, bookmark, subcategoryLabel)
  }

}
