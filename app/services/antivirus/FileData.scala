package services.antivirus

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime

case class FileData(
    id: String,
    externalId: String,
    creationDate: OffsetDateTime,
    filename: String,
    scanResult: Option[Int],
    avOutput: Option[String]
)
object FileData {
  implicit val fileFormat: OFormat[FileData] = Json.format[FileData]
}
