package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID

case class ReportNotificationBlocklist(
    userId: UUID,
    companyId: UUID,
    dateCreation: OffsetDateTime = OffsetDateTime.now
)

object ReportNotificationBlocklist {
  implicit val format: OFormat[ReportNotificationBlocklist] = Json.format[ReportNotificationBlocklist]
}
