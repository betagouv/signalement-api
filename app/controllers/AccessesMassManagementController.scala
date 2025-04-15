package controllers

import authentication.Authenticator
import models.User
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AccessesMassManagementController(
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseCompanyController(authenticator, controllerComponents) {

  def getCompaniesOfPro = Act.secured.pros.allowImpersonation.async { req =>
    for {
      companies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(req.identity)
    } yield Ok(Json.toJson(companies))
  }

  def getUsersOfPro = Act.secured.pros.allowImpersonation.async { _ =>
    Future.successful(Ok)
  }

}
