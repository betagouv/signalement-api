package models.report.signalconsoreview

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.util.UUID

case class SignalConsoReviewId(value: UUID) extends AnyVal

object SignalConsoReviewId {
  implicit val SignalConsoReviewIdFormat: Format[SignalConsoReviewId] =
    Json.valueFormat[SignalConsoReviewId]

  def generateId() = new SignalConsoReviewId(UUID.randomUUID())
}
