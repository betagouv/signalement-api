package models.report.reportmetadata

import models.report.ConsumerIp
import play.api.libs.json.{Json, Writes}

import java.util.UUID

case class ReportMetadata(
    reportId: UUID,
    isMobileApp: Boolean,
    os: Option[Os],
    assignedUserId: Option[UUID],
    consumerIp: Option[ConsumerIp]
)

object ReportMetadata {
  implicit val reportMetadataWrites: Writes[ReportMetadata] = Json.writes[ReportMetadata]
}