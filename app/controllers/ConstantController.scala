package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.UserRoles
import play.api.Logger
import play.api.libs.json.Json
import utils.Constants.ActionEvent
import utils.Constants.ReportStatus.reportStatusList
import utils.silhouette.auth.AuthEnv

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ConstantController @Inject()(val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getActions = SecuredAction.async { implicit request =>
    request.identity.userRole match {
      case UserRoles.Admin => Future.successful(Ok(Json.toJson(ActionEvent.actionsAdmin)))
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(ActionEvent.actionsDGCCRF)))
      case _ => Future.successful(Ok(Json.obj()))
    }
  }

  def getReportStatus = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(
      reportStatusList.map(_.getValueWithUserRole(request.identity.userRole)).distinct
    )))
  }

}
