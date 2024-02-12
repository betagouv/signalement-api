package models.report.socialnetwork

import models.report.SocialNetworkSlug
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

case class CertifiedInfluencer(id: UUID, socialNetwork: SocialNetworkSlug, name: String)

object CertifiedInfluencer {
  implicit val format: OFormat[CertifiedInfluencer] = Json.format[CertifiedInfluencer]
}
