package models.report.signalconsoreview

import models.report.reportmetadata.Os

import java.time.OffsetDateTime

case class SignalConsoReview(
    id: SignalConsoReviewId,
    evaluation: SignalConsoEvaluation,
    details: Option[String],
    creationDate: OffsetDateTime,
    platform: Os
)
