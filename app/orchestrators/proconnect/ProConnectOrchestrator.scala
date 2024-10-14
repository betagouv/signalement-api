package orchestrators.proconnect

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import orchestrators.socialmedia.SocialBladeClient.SocialBladeSupportedSocialNetwork
import play.api.Logger
import repositories.influencer.InfluencerRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ProConnectOrchestrator(
    proConnectClient: ProConnectClient
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

}
