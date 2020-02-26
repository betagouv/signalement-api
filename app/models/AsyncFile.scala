package models

import java.time.OffsetDateTime
import java.util.UUID

case class AsyncFile(
  id: UUID,
  userId: UUID,
  creationDate: OffsetDateTime,
  filename: Option[String],
  storageFilename: Option[String]
)
