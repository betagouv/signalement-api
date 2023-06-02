package models.report.signalconsoreview

import models.report.reportmetadata.Os
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime

case class SignalConsoReview(
    id: SignalConsoReviewId,
    evaluation: SignalConsoEvaluation,
    details: Option[String],
    creationDate: OffsetDateTime,
    platform: Os
)

object SignalConsoReview {
  implicit val SignalConsoReviewFormat: OFormat[SignalConsoReviewCreate] = Json.format[SignalConsoReviewCreate]
}
