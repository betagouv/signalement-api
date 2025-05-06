package controllers

import authentication.Authenticator
import models.User
import models.access.AccessesMassManagement.MassManagementInputs
import orchestrators._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class AccessesMassManagementController(
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    accessesMassManagementOrchestrator: AccessesMassManagementOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def getCompaniesOfPro = Act.secured.pros.allowImpersonation.async { req =>
    for {
      companies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(req.identity)
    } yield Ok(Json.toJson(companies))
  }

  def getUsersAndEmailsKnownToPro = Act.secured.pros.allowImpersonation.async { req =>
    for {
      usersAndEmails <- accessesMassManagementOrchestrator.getUsersAndEmailsKnownToPro(req.identity)
    } yield Ok(
      Json.toJson(usersAndEmails)
    )
  }

  def massManageAccesses = Act.secured.pros.forbidImpersonation.async(parse.json) { req =>
    for {
      inputs <- req.parseBody[MassManagementInputs]()
      _      <- accessesMassManagementOrchestrator.massManageAccesses(inputs, req.identity)
    } yield Ok
  }
}
