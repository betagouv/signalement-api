package orchestrators.socialmedia

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class SocialBladeStatus(success: Boolean, status: Int)

case class Id(username: String)

case class Data(id: Id)
case class SocialBladeResponse(status: SocialBladeStatus, data: Data)

object SocialBladeResponse {
  implicit val SocialBladeStatusFormat: OFormat[SocialBladeStatus]     = Json.format[SocialBladeStatus]
  implicit val IdFormat: OFormat[Id]                                   = Json.format[Id]
  implicit val DataFormat: OFormat[Data]                               = Json.format[Data]
  implicit val SocialBladeResponseFormat: OFormat[SocialBladeResponse] = Json.format[SocialBladeResponse]
}
