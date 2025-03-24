package models.report

import play.api.libs.json.{Json, OFormat}
import utils.URL

case class WebsiteURL(websiteURL: Option[URL], host: Option[String])

object WebsiteURL {
  implicit val WebsiteURLFormat: OFormat[WebsiteURL] = Json.format[WebsiteURL]
  val Empty: WebsiteURL = WebsiteURL(None, None)
}
