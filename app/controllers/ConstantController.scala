package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.UserRoles
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.AuthEnv

import scala.concurrent.{ExecutionContext, Future}
import utils.Constants.ActionEvent.{actionConsos, actionPros}
import utils.Constants.StatusPro.{status => statusPros}
import utils.Constants.StatusConso.{status => statusConsos}
import utils.Constants.StatusPro


@Singleton
class ConstantController @Inject()(val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getActionPros = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(actionPros)))
  }

  def getActionConsos = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(actionConsos)))
  }

  def getStatusPros = SecuredAction.async { implicit request =>
    request.identity.userRole match {
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(statusPros.filter(s => s != StatusPro.A_TRANSFERER_SIGNALEMENT))))
      case _ => Future.successful(Ok(Json.toJson(statusPros)))
    }

  }

  def getStatusConsos = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(statusConsos)))
  }

}
