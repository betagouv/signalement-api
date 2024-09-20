package controllers

import authentication.Authenticator
import authentication.actions.ImpersonationAction.ForbidImpersonation
import models.User
import models.UserRole
import models.report.ReportBlockedNotificationBody
import orchestrators.ReportBlockedNotificationOrchestrator
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import authentication.actions.UserAction.WithRole

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportBlockedNotificationController(
    authenticator: Authenticator[User],
    val orchestrator: ReportBlockedNotificationOrchestrator,
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  def getAll() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    orchestrator.findByUserId(request.identity.id).map(entities => Ok(Json.toJson(entities)))
  }

  def create() =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).andThen(ForbidImpersonation).async(parse.json) {
      implicit request =>
        request.body
          .validate[ReportBlockedNotificationBody]
          .fold(
            errors => Future.successful(BadRequest(JsError.toJson(errors))),
            body =>
              orchestrator
                .createIfNotExists(request.identity.id, body.companyIds)
                .map(entity => Ok(Json.toJson(entity)))
          )
    }

  def delete() =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).andThen(ForbidImpersonation).async(parse.json) {
      implicit request =>
        request.body
          .validate[ReportBlockedNotificationBody]
          .fold(
            errors => Future.successful(BadRequest(JsError.toJson(errors))),
            body => orchestrator.delete(request.identity.id, body.companyIds).map(_ => Ok)
          )
    }
}
