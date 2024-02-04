package controllers

import authentication.Authenticator
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

  def get(name: String, socialNetwork: SocialNetworkSlug) = Action.async { _ =>
    influencerOrchestrator
      .get(name, socialNetwork)
      .map(product => Ok(Json.toJson(product)))
  }

}
