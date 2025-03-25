package models.report

import models.company.Address
import models.company.Company
import models.report.reportmetadata.ReportMetadata
import repositories.bookmark.Bookmark
import repositories.subcategorylabel.SubcategoryLabel
import io.scalaland.chimney.dsl._
import models.MinimalUser
import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.OWrites

case class ReportWithMetadata(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    subcategoryLabel: Option[SubcategoryLabel]
)

case class ReportWithMetadataAndAlbertLabel(
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

object ReportWithMetadataAndAlbertLabel {
  def from(r: ReportWithMetadata, company: Option[Company]) = {
    val activityLabel = company.flatMap(_.albertActivityLabel)
    r.into[ReportWithMetadataAndAlbertLabel].withFieldConst(_.companyAlbertActivityLabel, activityLabel).transform
  }

}

case class ReportWithMetadataAndAlbertLabelAndFiles(
    report: Report,
    metadata: Option[ReportMetadata],
    isBookmarked: Boolean,
    assignedUser: Option[MinimalUser],
    files: List[ReportFileApi],
    companyAlbertActivityLabel: Option[String]
)
object ReportWithMetadataAndAlbertLabelAndFiles {
  implicit def writer(implicit userRole: Option[UserRole]): OWrites[ReportWithMetadataAndAlbertLabelAndFiles] =
    Json
      .writes[ReportWithMetadataAndAlbertLabelAndFiles]
      .contramap(r =>
        userRole match {
          case Some(UserRole.Professionnel) =>
            r.copy(companyAlbertActivityLabel = None)
          case _ => r
        }
      )
}
