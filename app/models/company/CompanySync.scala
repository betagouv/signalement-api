package models.company

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

final case class CompanySync(id: UUID, lastUpdated: OffsetDateTime)

object CompanySync {
  implicit val CompanySyncFormat: OFormat[CompanySync] = Json.format[CompanySync]

  // Truncated to MILLIS because PG does not handle nanos
  def default =
    new CompanySync(UUID.randomUUID(), OffsetDateTime.parse("1970-01-01T00:00:00+00:00").truncatedTo(ChronoUnit.MILLIS))
}
