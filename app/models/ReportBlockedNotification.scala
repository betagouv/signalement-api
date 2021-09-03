package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID

case class ReportBlockedNotification(
    userId: UUID,
    companyId: UUID,
    dateCreation: OffsetDateTime = OffsetDateTime.now
)

object ReportBlockedNotification {
  implicit val format: OFormat[ReportBlockedNotification] = Json.format[ReportBlockedNotification]
}
