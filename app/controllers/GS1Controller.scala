package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.gs1.GS1Product
import orchestrators.GS1Orchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import scala.concurrent.ExecutionContext

class GS1Controller(
    gs11Orchestrator: GS1Orchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  def getProductByGTIN(gtin: String) = UnsecuredAction.async { _ =>
    gs11Orchestrator.getByGTIN(gtin).map(_.map(r => Ok(Json.toJson(r)(GS1Product.writesToWebsite))).getOrElse(NotFound))
  }

  def getById(id: UUID) = SecuredAction.async { _ =>
    gs11Orchestrator.get(id).map(_.map(r => Ok(Json.toJson(r)(GS1Product.writesToDashboard))).getOrElse(NotFound))
  }

}
