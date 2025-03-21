package models.report

import models.report.reportmetadata.ReportMetadata
import models.report.review.{EngagementReview, ResponseConsumerReview}
import models.{MinimalUser, UserRole}
import play.api.libs.json.{Json, Writes}
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

case class ReportWithFiles(
                            report: Report,
                            metadata: Option[ReportMetadata],
                            bookmark: Option[Bookmark],
                            consumerReview: Option[ResponseConsumerReview],
                            engagementReview: Option[EngagementReview],
                            files: List[ReportFile]
                          )

case class ReportWithFilesAndResponses(
                                        report: Report,
                                        metadata: Option[ReportMetadata],
                                        bookmark: Option[Bookmark],
                                        consumerReview: Option[ResponseConsumerReview],
                                        engagementReview: Option[EngagementReview],
                                        files: List[ReportFile],
                                        assignedUser: Option[MinimalUser],
                                        proResponse: Option[EventWithUser],
                                      )

object ReportWithFilesAndResponses {
  implicit def writes(implicit userRole: Option[UserRole]): Writes[ReportWithFilesAndResponses] = (r: ReportWithFilesAndResponses) => userRole match {
    case Some(UserRole.Professionnel) =>
      Json.obj(
        "report" -> r.report,
        "assignedUser" -> r.assignedUser,
        "files" -> r.files,
        "consumerReview" -> r.consumerReview,
        "engagementReview" -> r.engagementReview,
        "professionalResponse" -> r.proResponse,
      )
    case _ =>
      Json.obj(
        "report" -> r.report,
        "isBookmarked" -> r.bookmark.isDefined,
        "files" -> r.files,
        "consumerReview" -> r.consumerReview,
        "engagementReview" -> r.engagementReview,
        "professionalResponse" -> r.proResponse,
      )
  }
}