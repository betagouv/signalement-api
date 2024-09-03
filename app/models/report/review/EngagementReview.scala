package models.report.review

import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.time.OffsetDateTime
import java.util.UUID

case class EngagementReview(
    id: ResponseConsumerReviewId,
    reportId: UUID,
    evaluation: ResponseEvaluation,
    creationDate: OffsetDateTime,
    details: Option[String]
)

object EngagementReview {
  implicit def engagementReviewWrites(implicit userRole: Option[UserRole]): Writes[EngagementReview] =
    (r: EngagementReview) =>
      Json.obj(
        "id"           -> r.id,
        "reportId"     -> r.reportId,
        "evaluation"   -> r.evaluation,
        "creationDate" -> r.creationDate
      ) ++ (if (userRole.exists(UserRole.isAdminOrAgent))
              Json.obj(
                "details" -> r.details
              )
            else Json.obj())
}
