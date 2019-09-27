package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.UserRoles
import play.api.Logger
import play.api.libs.json.Json
import utils.Constants.ActionEvent.{actionAgents, actionProFinals, actionPros}
import utils.Constants.StatusPro
import utils.Constants.StatusPro.{TRAITEMENT_EN_COURS, status => statusPros}
import utils.silhouette.auth.AuthEnv

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ConstantController @Inject()(val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getActionPros = SecuredAction.async { implicit request =>

    request.identity.userRole match {
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(actionProFinals)))
      case _ => Future.successful(Ok(Json.toJson(actionPros)))
    }
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

}
