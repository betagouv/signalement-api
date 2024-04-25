package models.report.review

import play.api.libs.json.Json
import play.api.libs.json.OFormat

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
  implicit val EngagementReviewFormat: OFormat[EngagementReview] = Json.format[EngagementReview]
}
