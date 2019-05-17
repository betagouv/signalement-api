package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class ReportFile(
                 id: UUID,
                 reportId: Option[UUID],
                 creationDate: LocalDateTime,
                 filename: String
                 )
object ReportFile {

  implicit val fileFormat: OFormat[ReportFile] = Json.format[ReportFile]

}