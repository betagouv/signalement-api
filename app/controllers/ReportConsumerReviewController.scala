package controllers

import com.mohiva.play.silhouette.api.Silhouette
import io.scalaland.chimney.dsl.TransformerOps
import models._
import models.report.review.ResponseConsumerReviewApi
import orchestrators.ReportConsumerReviewOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.auth.AuthEnv

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ReportConsumerReviewController @Inject() (
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def reviewOnReportResponse(reportId: String) = UnsecuredAction.async(parse.json) { implicit request =>
    for {
      review <- request.parseBody[ResponseConsumerReviewApi]()
      reportUUID = extractUUID(reportId)
      _ <- reportConsumerReviewOrchestrator.handleReviewOnReportResponse(reportUUID, review)
    } yield Ok
  }

  def getReview(reportId: String) = SecuredAction.async { _ =>
    logger.debug(s"Get report response review for report id : ${reportId}")
    val reportUUID = extractUUID(reportId)

    for {
      maybeResponseConsumerReview <- reportConsumerReviewOrchestrator.find(reportUUID)
      maybeResponseConsumerReviewApi = maybeResponseConsumerReview.map(_.into[ResponseConsumerReviewApi].transform)
    } yield Ok(Json.toJson(maybeResponseConsumerReviewApi))

  }

}
