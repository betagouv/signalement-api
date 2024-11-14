package orchestrators.proconnect

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ProConnectAccessToken(
    access_token: String,
    expires_in: Int,
    id_token: String
)

object ProConnectAccessToken {
  implicit val ProConnectAccessTokenFormat: OFormat[ProConnectAccessToken] = Json.format[ProConnectAccessToken]
}
