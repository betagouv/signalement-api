package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.report.ReportFilter
import orchestrators.UserReportsFiltersOrchestrator
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserReportsFiltersController(
    userReportsFiltersOrchestrator: UserReportsFiltersOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  case class UserReportsFiltersRequest(name: String, filters: ReportFilter)

  implicit val userReportsFiltersRequestReads: Reads[UserReportsFiltersRequest] = Json.reads[UserReportsFiltersRequest]

  def save(): Action[JsValue] =
    SecuredAction.async(parse.json) { implicit request =>
      request.body
        .validate[UserReportsFiltersRequest]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          userReportsFiltersRequest =>
            userReportsFiltersOrchestrator
              .save(request.identity.id, userReportsFiltersRequest.name, (request.body \ "filters").as[JsValue])
              .map(_ => NoContent)
        )
    }

  def get(name: String): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .get(request.identity.id, name)
        .map {
          case Some(userReportsFilters) => Ok(Json.toJson(userReportsFilters.reportsFilters))
          case None                     => NotFound
        }
    }

  def list(): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .list(request.identity.id)
        .map(userReportsFiltersList => Ok(Json.toJson(userReportsFiltersList)))
    }

  def delete(name: String): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .delete(request.identity.id, name)
        .map(_ => NoContent)
    }

  def rename(oldName: String, newName: String): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .rename(request.identity.id, oldName, newName)
        .map(_ => NoContent)
    }

  def setAsDefault(name: String): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .setAsDefault(request.identity.id, name)
        .map(_ => NoContent)
    }

  def unsetDefault(name: String): Action[AnyContent] =
    SecuredAction.async { implicit request =>
      userReportsFiltersOrchestrator
        .unsetDefault(request.identity.id, name)
        .map(_ => NoContent)
    }

}
