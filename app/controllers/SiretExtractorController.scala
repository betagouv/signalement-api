package controllers

import authentication.Authenticator
import models.User
import models.UserRole
import play.api.mvc.ControllerComponents
import services.SiretExtractorService
import authentication.actions.UserAction.WithRole

import scala.concurrent.ExecutionContext

class SiretExtractorController(
    siretExtractorService: SiretExtractorService,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def extractSiret() = SecuredAction.andThen(WithRole(UserRole.Admins)).async { request =>
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
