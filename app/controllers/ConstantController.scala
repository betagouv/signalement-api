package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.UserRoles
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.AuthEnv

import scala.concurrent.{ExecutionContext, Future}
import utils.Constants.ActionEvent.{actionAgents, actionConsos, actionProFinals, actionPros}
import utils.Constants.StatusPro.{TRAITEMENT_EN_COURS, status => statusPros}
import utils.Constants.StatusConso.{status => statusConsos}
import utils.Constants.StatusPro


@Singleton
class ConstantController @Inject()(val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getActionPros = SecuredAction.async { implicit request =>

    request.identity.userRole match {
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(actionProFinals)))
      case _ => Future.successful(Ok(Json.toJson(actionPros)))
    }
  }

  def getActionConsos = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(actionConsos)))
  }

  def getActionAgents = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(actionAgents)))
  }

  def getStatusPros = SecuredAction.async { implicit request =>

    request.identity.userRole match {
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(TRAITEMENT_EN_COURS +: StatusPro.statusFinals)))
      case _ => Future.successful(Ok(Json.toJson(statusPros)))
    }
  }

  def getActionProFinals = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(actionProFinals)))
  }

  def getStatusConsos = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(statusConsos)))
  }

}
