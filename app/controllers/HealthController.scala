package controllers

import authentication.Authenticator
import models.User
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class HealthController(
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def health =
    Act.public.standardLimit { _ =>
      Ok(Json.obj("name" -> "signalconso-api"))
    }

}
