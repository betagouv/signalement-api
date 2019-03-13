package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class File(
                 id: UUID,
                 reportId: Option[UUID],
                 creationDate: LocalDateTime,
                 filename: String
                 )
object File {

  implicit val fileFormat: OFormat[File] = Json.format[File]

}