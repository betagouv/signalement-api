package controllers

import authentication.Authenticator
import io.scalaland.chimney.dsl._
import models.User
import models.report.review.ConsumerReviewExistApi
import models.report.review.ResponseConsumerReviewApi
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

  def reviewOnReportResponse(reportUUID: UUID): Action[JsValue] = Action.async(parse.json) { implicit request =>
    for {
      review <- request.parseBody[ResponseConsumerReviewApi]()
      _      <- reportConsumerReviewOrchestrator.handleReviewOnReportResponse(reportUUID, review)
    } yield Ok
  }

  def getReview(reportUUID: UUID): Action[AnyContent] = SecuredAction.async { _ =>
    logger.debug(s"Get report response review for report id : ${reportUUID}")
    for {
      maybeResponseConsumerReview <- reportConsumerReviewOrchestrator.find(reportUUID)
      maybeResponseConsumerReviewApi = maybeResponseConsumerReview.map(_.into[ResponseConsumerReviewApi].transform)
    } yield Ok(Json.toJson(maybeResponseConsumerReviewApi))

  }

  def reviewExists(reportUUID: UUID): Action[AnyContent] = Action.async { _ =>
    logger.debug(s"Check if review exists for report id : ${reportUUID}")
    reportConsumerReviewOrchestrator.find(reportUUID).map(_.exists(_.details.nonEmpty)).map { exists =>
      Ok(Json.toJson(ConsumerReviewExistApi(exists)))
    }

  }

}
