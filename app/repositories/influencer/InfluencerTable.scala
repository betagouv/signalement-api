package repositories.influencer

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import slick.ast.TypedType

import java.time.OffsetDateTime

class InfluencerTable(tag: Tag)(implicit tt: TypedType[SocialNetworkSlug])
    extends DatabaseTable[CertifiedInfluencer](tag, "influencers") {
  def socialNetwork = column[SocialNetworkSlug]("social_network")

  def name         = column[String]("name")
  def creationDate = column[OffsetDateTime]("creation_date")

  override def * =
    (id, socialNetwork, name, creationDate) <> ((CertifiedInfluencer.apply _).tupled, CertifiedInfluencer.unapply)
}

object InfluencerTable {
  val table = TableQuery[InfluencerTable]
}
