package models

import java.time.OffsetDateTime
import java.util.UUID

case class Consumer(
    id: UUID,
    name: String,
    creationDate: OffsetDateTime,
    apiKey: String,
    deleteDate: Option[OffsetDateTime]
)
