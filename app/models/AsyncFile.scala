package models

import enumeratum._
import java.time.OffsetDateTime
import java.util.UUID

case class AsyncFile(
    id: UUID,
    userId: UUID,
    creationDate: OffsetDateTime,
    filename: Option[String],
    kind: AsyncFileKind,
    storageFilename: Option[String]
)

sealed abstract class AsyncFileKind(value: String) extends EnumEntry

object AsyncFileKind extends PlayEnum[AsyncFileKind] {

  val values = findValues

  case object Reports extends AsyncFileKind("Reports")

  case object ReportedPhones extends AsyncFileKind("ReportedPhones")

  case object ReportedWebsites extends AsyncFileKind("ReportedWebsites")
}
