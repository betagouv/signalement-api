package controllers

import authentication.Authenticator
import authentication.actions.UserAction.WithRole
import models.User
import models.UserRole
import models.promise.PromiseOfActionId
import orchestrators.PromiseOfActionOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class PromiseOfActionController(
    promiseOrchestrator: PromiseOfActionOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def list() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
    promiseOrchestrator.listForUser(request.identity).map(promises => Ok(Json.toJson(promises)))
  }

  def honour(id: PromiseOfActionId) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
      promiseOrchestrator.resolve(request.identity, id).map(_ => NoContent)
    }
}
