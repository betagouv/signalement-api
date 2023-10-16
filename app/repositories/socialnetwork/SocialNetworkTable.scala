package repositories.socialnetwork

import models.report.SocialNetworkSlug
import models.socialnetwork.SocialNetwork
import repositories.PostgresProfile.api._
import slick.ast.TypedType
import utils.SIRET

class SocialNetworkTable(tag: Tag)(implicit tt: TypedType[SocialNetworkSlug])
    extends Table[SocialNetwork](tag, "social_networks") {
  def slug  = column[SocialNetworkSlug]("slug", O.PrimaryKey)
  def siret = column[SIRET]("siret")

  override def * = (slug, siret) <> ((SocialNetwork.apply _).tupled, SocialNetwork.unapply)
}

object SocialNetworkTable {
  val table = TableQuery[SocialNetworkTable]
}
