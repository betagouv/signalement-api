package models.report.reportmetadata

import models.UserRole
import models.company.Address
import models.report.Report
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes
import repositories.bookmark.Bookmark

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

  def fromTuple(tuple: (Report, Option[ReportMetadata], Any)) = {
    val (report, metadata, _) = tuple
    ReportWithMetadata(report, metadata)
  }

  implicit def writes(implicit userRole: Option[UserRole]): OWrites[ReportWithMetadata] =
    Json.writes[ReportWithMetadata]

}

case class ReportWithMetadataAndBookmark(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark]
) {
  def setAddress(companyAddress: Address) =
    this.copy(
      report = this.report.copy(companyAddress = companyAddress)
    )
}
object ReportWithMetadataAndBookmark {
  def fromTuple(tuple: (Report, Option[ReportMetadata], Option[Bookmark])) = {
    val (report, metadata, bookmark) = tuple
    ReportWithMetadataAndBookmark(report, metadata, bookmark)
  }

  def fromTuple(tuple: (Report, Option[ReportMetadata], Option[Bookmark], Any)) = {
    val (report, metadata, bookmark, _) = tuple
    ReportWithMetadataAndBookmark(report, metadata, bookmark)
  }

}
