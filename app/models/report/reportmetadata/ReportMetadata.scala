package models.report.reportmetadata

import models.company.Address
import models.company.Company
import models.report.ConsumerIp
import models.report.Report
import play.api.libs.json.Json
import play.api.libs.json.Writes
import repositories.bookmark.Bookmark
import repositories.subcategorylabel.SubcategoryLabel
import io.scalaland.chimney.dsl._

import java.util.UUID

case class ReportMetadata(
    reportId: UUID,
    isMobileApp: Boolean,
    os: Option[Os],
    assignedUserId: Option[UUID],
    consumerIp: Option[ConsumerIp]
)

object ReportMetadata {
  implicit val reportMetadataWrites: Writes[ReportMetadata] = Json.writes[ReportMetadata]

}

case class ReportWithMetadataAndBookmark(
                                          report: Report,
                                          metadata: Option[ReportMetadata],
                                          bookmark: Option[Bookmark],
                                          subcategoryLabel: Option[SubcategoryLabel],
)

case class ReportExtra(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    subcategoryLabel: Option[SubcategoryLabel],
    companyAlbertActivityLabel: Option[String]
) {
  def setAddress(companyAddress: Address) =
    this.copy(
      report = this.report.copy(companyAddress = companyAddress)
    )
}

object ReportExtra {
  def from(r: ReportWithMetadataAndBookmark, company: Option[Company]) = {
    val activityLabel = company.flatMap(_.albertActivityLabel)
    r.into[ReportExtra].withFieldConst(_.companyAlbertActivityLabel, activityLabel).transform
  }

}
