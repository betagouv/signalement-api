package models.report.socialnetwork

import models.report.SocialNetworkSlug
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.util.UUID

case class CertifiedInfluencer(id: UUID, socialNetwork: SocialNetworkSlug, name: String, creationDate: OffsetDateTime)

object CertifiedInfluencer {
  implicit val format: OFormat[CertifiedInfluencer] = Json.format[CertifiedInfluencer]
}
