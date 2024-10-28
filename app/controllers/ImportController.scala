package controllers

import authentication.Authenticator
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
import authentication.actions.UserAction.WithRole
import models.company.AccessLevel

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ImportInput(
    siren: Option[SIREN],
    sirets: List[SIRET],
    emails: List[EmailAddress],
    onlyHeadOffice: Boolean,
    level: AccessLevel
)

case class MarketplaceImportInput(
    host: String,
    siret: SIRET
)

object ImportInput {
  implicit val importInputFormat: OFormat[ImportInput] = Json.format[ImportInput]
}

object MarketplaceImportInput {
  implicit val marketplaceImportInputFormat: OFormat[MarketplaceImportInput] = Json.format[MarketplaceImportInput]
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
      case (siren, sirets, emails) => Future.successful((siren, sirets, emails, input.onlyHeadOffice, input.level))
    }

  def importUsers = SecuredAction.andThen(WithRole(UserRole.Admins)).async(parse.json) { implicit request =>
    for {
      importInput                                    <- request.parseBody[ImportInput]()
      (siren, sirets, emails, onlyHeadOffice, level) <- validateInput(importInput)
      _ <- importOrchestrator.importUsers(siren, sirets, emails, onlyHeadOffice, level)
    } yield NoContent
  }

  def importMarketplaces() =
    SecuredAction.andThen(WithRole(UserRole.SuperAdmin)).async(parse.json) { implicit request =>
      for {
        input <- request.parseBody[List[MarketplaceImportInput]]()
        res   <- importOrchestrator.importMarketplaces(input.map(i => i.siret -> i.host), request.identity)
      } yield Ok(res.length.toString)
    }
}
