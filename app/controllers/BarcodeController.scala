package controllers

import authentication.Authenticator
import models.User
import models.barcode.BarcodeProduct
import orchestrators.BarcodeOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import java.util.UUID
import scala.concurrent.ExecutionContext

class BarcodeController(
    barcodeOrchestrator: BarcodeOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def getProductByGTIN(gtin: String) = IpRateLimitedAction3.async { _ =>
    barcodeOrchestrator
      .getByGTIN(gtin)
      .map(_.map(product => Ok(Json.toJson(product)(BarcodeProduct.writesToWebsite))).getOrElse(NotFound))
  }

  def getById(id: UUID) = SecuredAction.async { _ =>
    barcodeOrchestrator
      .get(id)
      .map(_.map(product => Ok(Json.toJson(product)(BarcodeProduct.writesToDashboard))).getOrElse(NotFound))
  }

}
