package controllers

import authentication.Authenticator
import authentication.actions.UserAction.WithRole
import io.scalaland.chimney.dsl.TransformationOps
import models.User
import models.UserRole
import models.engagement.EngagementId
import models.report.review.ConsumerReviewExistApi
import models.report.review.ResponseConsumerReviewApi
import orchestrators.EngagementOrchestrator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents

import java.util.UUID
import scala.concurrent.ExecutionContext

class EngagementController(
    engagementOrchestrator: EngagementOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def list() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
    engagementOrchestrator.listForUser(request.identity).map(engagements => Ok(Json.toJson(engagements)))
  }

  def check(id: EngagementId) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
      engagementOrchestrator.check(request.identity, id).map(_ => NoContent)
    }

  def uncheck(id: EngagementId) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
      engagementOrchestrator.uncheck(request.identity, id).map(_ => NoContent)
    }

  def reviewEngagementOnReportResponse(reportUUID: UUID): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      logger.debug(s"Push report engagement review for report id : $reportUUID")
      for {
        review <- request.parseBody[ResponseConsumerReviewApi]()
        _      <- engagementOrchestrator.handleEngagementReview(reportUUID, review)
      } yield Ok
  }

  def getEngagementReview(reportUUID: UUID): Action[AnyContent] = SecuredAction.async { _ =>
    logger.debug(s"Get report engagement review for report id : $reportUUID")
    for {
      maybeEngagementReview <- engagementOrchestrator.findEngagementReview(reportUUID)
      maybeResponseConsumerReviewApi = maybeEngagementReview.map(_.into[ResponseConsumerReviewApi].transform)
    } yield maybeResponseConsumerReviewApi
      .map(responseConsumerReviewApi => Ok(Json.toJson(responseConsumerReviewApi)))
      .getOrElse(NotFound)
  }

  def engagementReviewExists(reportUUID: UUID): Action[AnyContent] = Action.async { _ =>
    logger.debug(s"Check if engagement review exists for report id : $reportUUID")
    engagementOrchestrator.findEngagementReview(reportUUID).map(_.exists(_.details.nonEmpty)).map { exists =>
      Ok(Json.toJson(ConsumerReviewExistApi(exists)))
    }
  }
}
