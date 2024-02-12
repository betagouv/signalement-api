package orchestrators.socialmedia

import models.report.SocialNetworkSlug
import repositories.influencer.InfluencerRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class InfluencerOrchestrator(
    influencerRepository: InfluencerRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {

  def get(name: String, socialNetwork: SocialNetworkSlug): Future[Boolean] =
    influencerRepository.get(name, socialNetwork).map(_.nonEmpty)
}
