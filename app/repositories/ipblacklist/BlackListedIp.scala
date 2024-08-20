package repositories.ipblacklist

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class BlackListedIp(ip: String, comment: String, critical: Boolean)

object BlackListedIp {
  implicit val format: OFormat[BlackListedIp] = Json.format[BlackListedIp]
}
