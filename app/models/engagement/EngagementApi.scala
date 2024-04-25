package models.engagement

import models.UserRole
import models.report.Report
import models.report.ResponseDetails
import play.api.libs.json.Json
import play.api.libs.json.OWrites

import java.time.OffsetDateTime

case class EngagementApi(
    id: EngagementId,
    report: Report,
    engagement: ResponseDetails,
    otherEngagement: Option[String],
    expirationDate: OffsetDateTime,
    resolutionDate: Option[OffsetDateTime]
)

object EngagementApi {
  implicit def writes(implicit userRole: Option[UserRole]): OWrites[EngagementApi] =
    Json.writes[EngagementApi]
}
