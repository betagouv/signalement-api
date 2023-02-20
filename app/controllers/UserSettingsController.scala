package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.report.ReportFilter
import orchestrators.UserSettingsOrchestrator
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserSettingsController(
    userSettingsOrchestrator: UserSettingsOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  def saveReportsFilters(): Action[JsValue] =
    SecuredAction.async(parse.json) { implicit request =>
      request.body
        .validate[ReportFilter]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          _ =>
            userSettingsOrchestrator
              .saveReportsFilters(request.identity.id, request.body)
              .map(_ => NoContent)
        )
    }

  def getReportsFilters(): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userSettingsOrchestrator
        .getReportsFilters(request.identity.id)
        .map {
          case Some(settings) => Ok(Json.toJson(settings.reportsFilters))
          case None           => NotFound
        }
    }

}
