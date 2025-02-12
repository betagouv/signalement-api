package controllers

import authentication.Authenticator
import cats.implicits.toTraverseOps
import models.User
import models.report.SocialNetworkSlug
import orchestrators.socialmedia.InfluencerOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class SocialNetworkController(
    influencerOrchestrator: InfluencerOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def checkIfInfluencerExists(name: String, socialNetwork: String) = Act.public.tightLimit.async { _ =>
    SocialNetworkSlug
      .withNameInsensitiveOption(socialNetwork)
      .traverse(socialNetworkSlug => influencerOrchestrator.exist(name, socialNetworkSlug))
      .map(_.getOrElse(false))
      .map(result => Ok(Json.toJson(result)))

  }

}
