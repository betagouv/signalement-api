package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID
import java.time.temporal.ChronoUnit
case class ReportBlockedNotification(
    userId: UUID,
    companyId: UUID,
    dateCreation: OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
)

object ReportBlockedNotification {
  implicit val format: OFormat[ReportBlockedNotification] = Json.format[ReportBlockedNotification]
}

case class ReportBlockedNotificationBody(companyIds: Seq[UUID])

object ReportBlockedNotificationBody {
  implicit val format: OFormat[ReportBlockedNotificationBody] = Json.format[ReportBlockedNotificationBody]
}
