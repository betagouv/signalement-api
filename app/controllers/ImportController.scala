package controllers

import controllers.error.AppError.EmptyEmails
import models.User
import models.UserRole
import orchestrators.ImportOrchestratorInterface
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.ControllerComponents
import utils.EmailAddress
import utils.SIREN
import utils.SIRET
import utils.auth.Authenticator
import utils.auth.UserAction.WithRole

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ImportInput(siren: Option[SIREN], sirets: List[SIRET], emails: List[EmailAddress])

object ImportInput {
  implicit val test: OFormat[ImportInput] = Json.format[ImportInput]
}

class ImportController(
    importOrchestrator: ImportOrchestratorInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  private def validateInput(input: ImportInput) =
    (input.siren, input.sirets, input.emails) match {
      case (_, _, Nil)             => Future.failed(EmptyEmails)
      case (None, Nil, _)          => Future.failed(EmptyEmails)
      case (siren, sirets, emails) => Future.successful((siren, sirets, emails))
    }

  def importUsers = SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    for {
      importInput             <- request.parseBody[ImportInput]()
      (siren, sirets, emails) <- validateInput(importInput)
      _                       <- importOrchestrator.importUsers(siren, sirets, emails)
    } yield NoContent
  }
}
