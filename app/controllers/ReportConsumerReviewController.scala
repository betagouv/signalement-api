package controllers

import authentication.Authenticator
import models.User
import models.report.review.ResponseConsumerReview.responseConsumerReviewWrites
import models.report.review.ConsumerReviewExistApi
import models.report.review.ConsumerReviewApi
import models.report.review.ConsumerReviewApi._
import orchestrators.ReportConsumerReviewOrchestrator
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents

import java.util.UUID
import scala.concurrent.ExecutionContext

class ReportConsumerReviewController(
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def reviewOnReportResponse(reportUUID: UUID): Action[JsValue] = IpRateLimitedAction2.async(parse.json) {
    implicit request =>
      for {
        review <- request.parseBody[ConsumerReviewApi]()
        _      <- reportConsumerReviewOrchestrator.handleReviewOnReportResponse(reportUUID, review)
      } yield Ok
  }

  def getReview(reportUUID: UUID): Action[AnyContent] = SecuredAction.async { request =>
    logger.debug(s"Get report response review for report id : ${reportUUID}")
    for {
      maybeReview <- reportConsumerReviewOrchestrator.find(reportUUID)
    } yield maybeReview
      .map { review =>
        val writes = responseConsumerReviewWrites(Some(request.identity.userRole))
        Ok(Json.toJson(review)(writes))
      }
      .getOrElse(NotFound)

  }

  def reviewExists(reportUUID: UUID): Action[AnyContent] = IpRateLimitedAction2.async { _ =>
    logger.debug(s"Check if review exists for report id : ${reportUUID}")
    reportConsumerReviewOrchestrator.find(reportUUID).map(_.exists(_.details.nonEmpty)).map { exists =>
      Ok(Json.toJson(ConsumerReviewExistApi(exists)))
    }

  }

}
