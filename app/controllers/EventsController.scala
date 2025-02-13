package controllers

import authentication.Authenticator
import models.User
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import orchestrators.EventsOrchestratorInterface
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext

class EventsController(
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    eventsOrchestrator: EventsOrchestratorInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseCompanyController(authenticator, controllerComponents) {

  def getCompanyEvents(siret: SIRET, eventType: Option[String]): Action[AnyContent] =
    Act.securedWithCompanyAccessBySiret(siret.toString).allowImpersonation.async { implicit request =>
      logger.info(s"Fetching events for company $siret with eventType $eventType")
      eventsOrchestrator
        .getCompanyEvents(siret = siret, eventType = eventType, userRole = request.identity.userRole)
        .map(events => Ok(Json.toJson(events)))
    }

  def getReportEvents(reportId: UUID, eventType: Option[String]): Action[AnyContent] =
    Act.secured.all.allowImpersonation.async { implicit request =>
      logger.info(s"Fetching events for report $reportId with eventType $eventType")
      eventsOrchestrator
        .getReportsEvents(reportId = reportId, eventType = eventType, user = request.identity)
        .map(events => Ok(Json.toJson(events)))
    }

}
