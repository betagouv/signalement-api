package controllers

import authentication.Authenticator
import models.Rating
import models.User
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.rating.RatingRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RatingController(
    ratingRepository: RatingRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def rate = Act.public.standardLimit.async(parse.json) { implicit request =>
    request.body
      .validate[Rating]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        rating =>
          ratingRepository
            .create(
              rating.copy(
                id = Some(UUID.randomUUID()),
                creationDate = Some(OffsetDateTime.now())
              )
            )
            .map(rating => Ok(Json.toJson(rating)))
      )
  }
}
