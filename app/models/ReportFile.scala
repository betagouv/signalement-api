package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class ReportFile(
                 id: UUID,
                 reportId: Option[UUID],
                 creationDate: OffsetDateTime,
                 filename: String
                 )
object ReportFile {

  implicit val fileFormat: OFormat[ReportFile] = Json.format[ReportFile]

}