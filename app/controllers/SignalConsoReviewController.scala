package controllers

import com.mohiva.play.silhouette.api.Silhouette
import io.scalaland.chimney.dsl.TransformerOps
import models.report.signalconsoreview.SignalConsoReview
import models.report.signalconsoreview.SignalConsoReviewCreate
import models.report.signalconsoreview.SignalConsoReviewId
import play.api.mvc.ControllerComponents
import repositories.signalconsoreview.SignalConsoReviewRepositoryInterface
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext

class SignalConsoReviewController(
    repository: SignalConsoReviewRepositoryInterface,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(controllerComponents) {

  def signalConsoReview() = UnsecuredAction.async(parse.json) { implicit request =>
    for {
      reviewCreate <- request.parseBody[SignalConsoReviewCreate]()
      review = reviewCreate.into[SignalConsoReview].withFieldConst(_.id, SignalConsoReviewId.generateId()).transform
      _ <- repository.create(review)
    } yield NoContent

  }
}
