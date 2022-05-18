package controllers

import com.mohiva.play.silhouette.api.Silhouette
import io.scalaland.chimney.dsl.TransformerOps
import models.report.review.ResponseConsumerReviewApi
import orchestrators.ReportConsumerReviewOrchestrator
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ReportConsumerReviewController @Inject() (
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def reviewOnReportResponse(reportUUID: UUID): Action[JsValue] = UnsecuredAction.async(parse.json) {
    implicit request =>
      for {
        review <- request.parseBody[ResponseConsumerReviewApi]()
        _ <- reportConsumerReviewOrchestrator.handleReviewOnReportResponse(reportUUID, review)
      } yield Ok
  }

  def getReview(reportUUID: UUID): Action[AnyContent] = SecuredAction.async { _ =>
    logger.debug(s"Get report response review for report id : ${reportUUID}")
    for {
      maybeResponseConsumerReview <- reportConsumerReviewOrchestrator.find(reportUUID)
      maybeResponseConsumerReviewApi = maybeResponseConsumerReview.map(_.into[ResponseConsumerReviewApi].transform)
    } yield Ok(Json.toJson(maybeResponseConsumerReviewApi))

  }

}
