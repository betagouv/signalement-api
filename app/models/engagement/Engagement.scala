package models.engagement

import java.time.OffsetDateTime
import java.util.UUID

case class Engagement(
    id: EngagementId,
    reportId: UUID,
    promiseEventId: UUID,
    resolutionEventId: Option[UUID],
    expirationDate: OffsetDateTime
)

object Engagement {
  val EngagementReminderPeriod = 21
}
