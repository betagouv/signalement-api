package models

import java.time.OffsetDateTime
import java.util.UUID

final case class EnterpriseSyncInfo(
  id: UUID = UUID.randomUUID(),
  fileName: String,
  fileUrl: String,
  linesCount: Double,
  linesDone: Double = 0,
  startedAt: OffsetDateTime = OffsetDateTime.now,
  endedAt: Option[OffsetDateTime] = None,
  errors: Option[String] = None,
)

final case class EnterpriseSyncInfoUpdate(
  linesDone: Option[Double] = None,
  endedAt: Option[OffsetDateTime] = None,
  errors: Option[String] = None,
)