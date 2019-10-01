package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.UserRoles
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import utils.Constants.{ActionEvent, StatusPro}
import utils.Constants.StatusPro.{StatusProValue, TRAITEMENT_EN_COURS, status => statusPros}
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

  def getStatusPros = SecuredAction.async { implicit request =>

    request.identity.userRole match {
      case UserRoles.DGCCRF => Future.successful(Ok(Json.toJson(StatusPro.statusDGCCRF)))
      case _ => Future.successful(Ok(Json.toJson(statusPros)))
    }
  }

}
