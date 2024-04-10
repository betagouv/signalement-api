package models.promise

import models.UserRole
import models.report.Report
import models.report.ResponseDetails
import play.api.libs.json.Json
import play.api.libs.json.OWrites

import java.time.OffsetDateTime

case class PromiseOfActionApi(
    id: PromiseOfActionId,
    report: Report,
    expirationDate: OffsetDateTime,
    engagement: ResponseDetails,
    otherEngagement: Option[String],
    resolutionDate: Option[OffsetDateTime]
)

object PromiseOfActionApi {
  implicit def writes(implicit userRole: Option[UserRole]): OWrites[PromiseOfActionApi] =
    Json.writes[PromiseOfActionApi]
}
