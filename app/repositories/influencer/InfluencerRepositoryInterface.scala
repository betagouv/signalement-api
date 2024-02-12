package repositories.influencer

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait InfluencerRepositoryInterface extends CRUDRepositoryInterface[CertifiedInfluencer] {
  def get(name: String, socialNetwork: SocialNetworkSlug): Future[Seq[CertifiedInfluencer]]
}
