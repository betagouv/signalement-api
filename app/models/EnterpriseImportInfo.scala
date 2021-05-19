package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.Json

final case class EnterpriseImportInfo(
  id: UUID = UUID.randomUUID(),
  fileName: String,
  fileUrl: String,
  linesCount: Double,
  linesDone: Double = 0,
  startedAt: OffsetDateTime = OffsetDateTime.now,
  endedAt: Option[OffsetDateTime] = None,
  errors: Option[String] = None,
)

object EnterpriseImportInfo {
  implicit val enterpriseSyncWrite = Json.writes[EnterpriseImportInfo]
}