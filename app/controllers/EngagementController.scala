package controllers

import authentication.Authenticator
import models.User
import models.UserRole
import models.engagement.EngagementId
import models.report.review.EngagementReview.engagementReviewWrites
import models.report.review.ConsumerReviewExistApi
import models.report.review.ConsumerReviewApi
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

  def list() = Act.secured.pros.allowImpersonation.async { implicit request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
    engagementOrchestrator.listForUser(request.identity).map(engagements => Ok(Json.toJson(engagements)))
  }

  def check(id: EngagementId) =
    Act.secured.pros.forbidImpersonation.async { implicit request =>
      engagementOrchestrator.check(request.identity, id).map(_ => NoContent)
    }

  def uncheck(id: EngagementId) =
    Act.secured.pros.forbidImpersonation.async { implicit request =>
      engagementOrchestrator.uncheck(request.identity, id).map(_ => NoContent)
    }

  def reviewEngagementOnReportResponse(reportUUID: UUID): Action[JsValue] = Act.public.standardLimit.async(parse.json) {
    implicit request =>
      logger.debug(s"Push report engagement review for report id : $reportUUID")
      for {
        review <- request.parseBody[ConsumerReviewApi]()
        _      <- engagementOrchestrator.handleEngagementReview(reportUUID, review)
      } yield Ok
  }

  def getEngagementReview(reportUUID: UUID): Action[AnyContent] = Act.secured.all.allowImpersonation.async { request =>
    logger.debug(s"Get report engagement review for report id : $reportUUID")
    for {
      maybeReview <- engagementOrchestrator.getVisibleEngagementReview(reportUUID, request.identity)
    } yield maybeReview
      .map { review =>
        val writes = engagementReviewWrites(Some(request.identity.userRole))
        Ok(Json.toJson(review)(writes))
      }
      .getOrElse(NotFound)
  }

  def engagementReviewExists(reportUUID: UUID): Action[AnyContent] = Act.public.standardLimit.async { _ =>
    logger.debug(s"Check if engagement review exists for report id : $reportUUID")
    engagementOrchestrator.doesEngagementReviewExists(reportUUID).map { exists =>
      Ok(Json.toJson(ConsumerReviewExistApi(exists)))
    }
  }
}
