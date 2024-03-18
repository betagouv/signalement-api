package orchestrators.socialmedia

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import orchestrators.socialmedia.SocialBladeClient.SocialBladeSupportedSocialNetwork
import play.api.Logger
import repositories.influencer.InfluencerRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class InfluencerOrchestrator(
    influencerRepository: InfluencerRepositoryInterface,
    socialBladeClient: SocialBladeClient
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)
  def exist(name: String, socialNetwork: SocialNetworkSlug): Future[Boolean] = {
    val curated = name.toLowerCase.replaceAll("\\s", "")
    influencerRepository.get(curated, socialNetwork).flatMap { signalConsoCertifiedInfluencers =>
      if (signalConsoCertifiedInfluencers.nonEmpty) {
        Future.successful(true)
      } else if (SocialBladeSupportedSocialNetwork.contains(socialNetwork)) {
        checkSocialNetworkUsername(curated, socialNetwork)
      } else {
        logger.debug(s"SocialNetwork $socialNetwork network not supported by social blade")
        Future.successful(false)
      }
    }
  }

  private def checkSocialNetworkUsername(curatedUsername: String, socialNetwork: SocialNetworkSlug) =
    socialBladeClient.checkSocialNetworkUsername(socialNetwork, curatedUsername).flatMap { existsOnSocialBlade =>
      if (existsOnSocialBlade.isDefined) {
        influencerRepository
          .create(
            CertifiedInfluencer(
              UUID.randomUUID(),
              socialNetwork,
              curatedUsername,
              OffsetDateTime.now(),
              existsOnSocialBlade.flatMap(_.followers)
            )
          )
          .map(_ => true)
      } else {
        Future.successful(false)
      }
    }

}
