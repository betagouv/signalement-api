package orchestrators.socialmedia

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import repositories.influencer.InfluencerRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class InfluencerOrchestrator(
    influencerRepository: InfluencerRepositoryInterface,
    socialBladeClient: SocialBladeClient
)(implicit
    val executionContext: ExecutionContext
) {

  def exist(name: String, socialNetwork: SocialNetworkSlug): Future[Boolean] =
    influencerRepository.get(name, socialNetwork).flatMap { signalConsoCertifiedInfluencers =>
      if (signalConsoCertifiedInfluencers.nonEmpty) {
        Future.successful(true)
      } else {
        socialBladeClient.checkSocialNetworkUsername(socialNetwork, name).flatMap { existsOnSocialBlade =>
          if (existsOnSocialBlade) {
            influencerRepository.create(CertifiedInfluencer(UUID.randomUUID(), socialNetwork, name)).map(_ => true)
          } else {
            Future.successful(false)
          }
        }
      }
    }

}
