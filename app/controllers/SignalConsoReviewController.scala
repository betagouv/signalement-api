package controllers

import authentication.Authenticator
import io.scalaland.chimney.dsl._
import models.User
import models.report.signalconsoreview.SignalConsoReview
import models.report.signalconsoreview.SignalConsoReviewCreate
import models.report.signalconsoreview.SignalConsoReviewId
import play.api.mvc.ControllerComponents
import repositories.signalconsoreview.SignalConsoReviewRepositoryInterface

import scala.concurrent.ExecutionContext

class SignalConsoReviewController(
    repository: SignalConsoReviewRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  def signalConsoReview() = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      reviewCreate <- request.parseBody[SignalConsoReviewCreate]()
      review = reviewCreate.into[SignalConsoReview].withFieldConst(_.id, SignalConsoReviewId.generateId()).transform
      _ <- repository.create(review)
    } yield NoContent

  }
}
