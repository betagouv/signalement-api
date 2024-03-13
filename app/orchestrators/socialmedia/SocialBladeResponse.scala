package orchestrators.socialmedia

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class SocialBladeStatus(success: Boolean, status: Int)

case class Id(username: String)
case class Total(subscribers: Option[Int], followers: Option[Int], likes: Option[Int])
case class Statistics(total: Total)
case class Data(id: Id, statistics: Statistics)
case class SocialBladeResponse(status: SocialBladeStatus, data: Data)

object SocialBladeResponse {
  implicit val SocialBladeStatusFormat: OFormat[SocialBladeStatus]     = Json.format[SocialBladeStatus]
  implicit val IdFormat: OFormat[Id]                                   = Json.format[Id]
  implicit val TotalFormat: OFormat[Total]                             = Json.format[Total]
  implicit val StatisticsFormat: OFormat[Statistics]                   = Json.format[Statistics]
  implicit val DataFormat: OFormat[Data]                               = Json.format[Data]
  implicit val SocialBladeResponseFormat: OFormat[SocialBladeResponse] = Json.format[SocialBladeResponse]
}
