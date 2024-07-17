package services.antivirus

import play.api.libs.json._

case class ScanCommand(
    externalId: String,
    filename: String
)
object ScanCommand {
  implicit val ScanCommandFormat: OFormat[ScanCommand] = Json.format[ScanCommand]
}
