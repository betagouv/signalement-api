package models.report

import models.event.EventWithUser
import models.report.reportmetadata.ReportMetadata
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReview
import models.MinimalUser
import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.Writes
import repositories.bookmark.Bookmark
import repositories.subcategorylabel.SubcategoryLabel

case class ReportFromSearch(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    consumerReview: Option[ResponseConsumerReview],
    engagementReview: Option[EngagementReview],
    subcategoryLabel: Option[SubcategoryLabel]
)

case class ReportFromSearchWithFiles(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    consumerReview: Option[ResponseConsumerReview],
    engagementReview: Option[EngagementReview],
    files: List[ReportFile]
)

case class ReportFromSearchWithFilesAndResponses(
    report: Report,
    metadata: Option[ReportMetadata],
    bookmark: Option[Bookmark],
    consumerReview: Option[ResponseConsumerReview],
    engagementReview: Option[EngagementReview],
    files: List[ReportFile],
    assignedUser: Option[MinimalUser],
    proResponse: Option[EventWithUser]
)

object ReportFromSearchWithFilesAndResponses {
  implicit def writes(implicit userRole: Option[UserRole]): Writes[ReportFromSearchWithFilesAndResponses] =
    (r: ReportFromSearchWithFilesAndResponses) =>
      userRole match {
        case Some(UserRole.Professionnel) =>
          Json.obj(
            "report"               -> r.report,
            "assignedUser"         -> r.assignedUser,
            "files"                -> r.files,
            "consumerReview"       -> r.consumerReview,
            "engagementReview"     -> r.engagementReview,
            "professionalResponse" -> r.proResponse
          )
        case _ =>
          Json.obj(
            "report"               -> r.report,
            "isBookmarked"         -> r.bookmark.isDefined,
            "files"                -> r.files,
            "consumerReview"       -> r.consumerReview,
            "engagementReview"     -> r.engagementReview,
            "professionalResponse" -> r.proResponse
          )
      }
}
