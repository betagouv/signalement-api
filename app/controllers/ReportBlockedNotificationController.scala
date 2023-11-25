package controllers

import models.UserRole
import models.report.ReportBlockedNotificationBody
import orchestrators.ReportBlockedNotificationOrchestrator
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import .WithRole
import utils.auth.CookieAuthenticator

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportBlockedNotificationController(
    val authenticator: CookieAuthenticator,
    val orchestrator: ReportBlockedNotificationOrchestrator,
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  def getAll() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    orchestrator.findByUserId(request.user.id).map(entities => Ok(Json.toJson(entities)))
  }

  def create() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportBlockedNotificationBody]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        body =>
          orchestrator.createIfNotExists(request.user.id, body.companyIds).map(entity => Ok(Json.toJson(entity)))
      )
  }

  def delete() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportBlockedNotificationBody]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        body => orchestrator.delete(request.user.id, body.companyIds).map(_ => Ok)
      )
  }
}
