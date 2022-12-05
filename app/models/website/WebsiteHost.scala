package models.website

import play.api.libs.json.Format
import play.api.libs.json.Json

case class WebsiteHost(value: String) extends AnyVal

object WebsiteHost {
  implicit val HostFormat: Format[WebsiteHost] = Json.valueFormat[WebsiteHost]
}
