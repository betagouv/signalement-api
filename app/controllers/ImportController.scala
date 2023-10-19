package controllers

import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppError.EmptyEmails
import models.UserRole
import orchestrators.ImportOrchestratorInterface
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.ControllerComponents
import utils.EmailAddress
import utils.SIREN
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ImportInput(siren: Option[SIREN], sirets: List[SIRET], emails: List[EmailAddress])

object ImportInput {
  implicit val test: OFormat[ImportInput] = Json.format[ImportInput]
}

class ImportController(
    importOrchestrator: ImportOrchestratorInterface,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  private def validateInput(input: ImportInput) =
    (input.siren, input.sirets, input.emails) match {
      case (_, _, Nil)             => Future.failed(EmptyEmails)
      case (None, Nil, _)          => Future.failed(EmptyEmails)
      case (siren, sirets, emails) => Future.successful((siren, sirets, emails))
    }

  def importUsers = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    for {
      importInput             <- request.parseBody[ImportInput]()
      (siren, sirets, emails) <- validateInput(importInput)
      _                       <- importOrchestrator.importUsers(siren, sirets, emails)
    } yield NoContent
  }
}
