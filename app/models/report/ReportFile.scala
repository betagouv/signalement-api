package models.report

import enumeratum.EnumEntry.Lowercase
import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.report.reportfile.ReportFileId
import play.api.libs.json._

import java.time.OffsetDateTime
import java.util.UUID

sealed trait ReportFileOrigin extends EnumEntry with Lowercase

object ReportFileOrigin extends PlayEnum[ReportFileOrigin] {
  case object Consumer     extends ReportFileOrigin
  case object Professional extends ReportFileOrigin
  override def values: IndexedSeq[ReportFileOrigin] = findValues
}

case class ReportFile(
    id: ReportFileId,
    reportId: Option[UUID],
    creationDate: OffsetDateTime,
    filename: String,
    storageFilename: String,
    origin: ReportFileOrigin,
    avOutput: Option[String]
)
object ReportFile {
  implicit val fileFormat: OFormat[ReportFile] = Json.format[ReportFile]
  val MaxFileNameLength                        = 200
}
