package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class Influencer(socialNetwork: Option[SocialNetworkSlug], otherSocialNetwork: Option[String], name: String)

object Influencer {
  implicit val format: OFormat[Influencer] = Json.format[Influencer]
}
