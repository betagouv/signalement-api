package models.report.signalconsoreview

import models.report.reportmetadata.Os
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime

case class SignalConsoReviewCreate(
    evaluation: SignalConsoEvaluation,
    details: Option[String],
    creationDate: OffsetDateTime,
    platform: Os
)

object SignalConsoReviewCreate {
  implicit val SignalConsoReviewFormat: OFormat[SignalConsoReviewCreate] = Json.format[SignalConsoReviewCreate]
}
