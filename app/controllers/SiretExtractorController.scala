package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.UserRole
import play.api.mvc.ControllerComponents
import services.SiretExtractorService
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import scala.concurrent.ExecutionContext

class SiretExtractorController(
    siretExtractorService: SiretExtractorService,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  def extractSiret() = SecuredAction(WithRole(UserRole.Admin)).async { request =>
    siretExtractorService
      .extractSiret(request.body.asJson)
      .map { response =>
        response.body match {
          case Left(body)  => Status(response.code.code)(body.getMessage)
          case Right(body) => Status(response.code.code)(body)
        }
      }
  }

}
