package models

import enumeratum._

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

case class AsyncFile(
    id: UUID,
    userId: UUID,
    creationDate: OffsetDateTime,
    filename: Option[String],
    kind: AsyncFileKind,
    storageFilename: Option[String]
)

object AsyncFile {
  def build(owner: User, kind: AsyncFileKind): AsyncFile = AsyncFile(
    id = UUID.randomUUID(),
    userId = owner.id,
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    kind = kind,
    filename = None,
    storageFilename = None
  )
}

sealed trait AsyncFileKind extends EnumEntry

object AsyncFileKind extends PlayEnum[AsyncFileKind] {

  val values = findValues

  case object Reports extends AsyncFileKind

  case object ReportedPhones extends AsyncFileKind

  case object ReportedWebsites extends AsyncFileKind
}
