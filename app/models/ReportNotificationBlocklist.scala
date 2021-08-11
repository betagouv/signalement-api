package models

import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime
import java.util.UUID

case class ReportNotificationBlocklist(
  userId: UUID,
  companyId: UUID,
  dateCreation: OffsetDateTime = OffsetDateTime.now,
) {

  val format: OFormat[ReportNotificationBlocklist] = Json.format[ReportNotificationBlocklist]

  def toJson() {
    Json.toJson(this)(format)
  }

  def toJson(company: Company): Unit = {

  }
}

object ReportNotificationBlocklist {

}