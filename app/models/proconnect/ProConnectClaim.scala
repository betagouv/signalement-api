package models.proconnect

import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.JsonNaming
import play.api.libs.json.Reads

case class ProConnectClaim(
    sub: String,
    email: String,
    givenName: String,
    usualName: String,
    aud: String,
    exp: Long,
    iat: Long,
    iss: String,
    idp_id: String
)

object ProConnectClaim {

  implicit val config: JsonConfiguration.Aux[Json.MacroOptions] = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val proConnectClaimReads: Reads[ProConnectClaim] = Json.reads[ProConnectClaim]

}
