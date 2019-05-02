package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.AuthEnv

import scala.concurrent.ExecutionContext
import utils.Constants.ActionEvent.{actionConsos, actionPros}
import utils.Constants.StatusPro.{ status => statusPros }
import utils.Constants.StatusConso.{ status => statusConsos }


@Singleton
class ConstantController @Inject()(val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getActionPros = UserAwareAction { implicit request =>
    Ok(Json.toJson(actionPros))
  }

  def getActionConsos = UserAwareAction { implicit request =>
    Ok(Json.toJson(actionConsos))
  }

  def getStatusPros = UserAwareAction { implicit request =>
    Ok(Json.toJson(statusPros))
  }

  def getStatusConsos = UserAwareAction { implicit request =>
    Ok(Json.toJson(statusConsos))
  }

}
