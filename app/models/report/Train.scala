package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class Train(train: String, ter: Option[String], nightTrain: Option[String])

object Train {
  implicit val format: OFormat[Train] = Json.format[Train]
}
