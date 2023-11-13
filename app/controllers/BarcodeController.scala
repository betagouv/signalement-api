package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.barcode.BarcodeProduct
import orchestrators.BarcodeOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import scala.concurrent.ExecutionContext

class BarcodeController(
    gs11Orchestrator: BarcodeOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  def getProductByGTIN(gtin: String) = UnsecuredAction.async { _ =>
    gs11Orchestrator
      .getByGTIN(gtin)
      .map(_.map(product => Ok(Json.toJson(product)(BarcodeProduct.writesToWebsite))).getOrElse(NotFound))
  }

  def getById(id: UUID) = SecuredAction.async { _ =>
    gs11Orchestrator
      .get(id)
      .map(_.map(product => Ok(Json.toJson(product)(BarcodeProduct.writesToDashboard))).getOrElse(NotFound))
  }

}
