package orchestrators.socialmedia

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import repositories.influencer.InfluencerRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class InfluencerOrchestrator(
    influencerRepository: InfluencerRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {

  def get(name: String, socialNetwork: SocialNetworkSlug): Future[Seq[CertifiedInfluencer]] =
    influencerRepository.get(name, socialNetwork)
}
