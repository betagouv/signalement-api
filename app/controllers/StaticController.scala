package controllers

import models.User
import play.api.mvc.ControllerComponents
import utils.auth.Authenticator

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StaticController(authenticator: Authenticator[User], controllerComponents: ControllerComponents)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  def api = UserAwareAction.async(parse.empty) { _ =>
    Future.successful(Ok(views.html.api()))
  }
}
