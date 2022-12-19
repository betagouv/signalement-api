package models

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

case class Consumer(
    id: UUID = UUID.randomUUID(),
    name: String,
    creationDate: OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    apiKey: String,
    deleteDate: Option[OffsetDateTime] = None
)
