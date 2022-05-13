package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.Rating
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories.rating.RatingRepositoryInterface
import utils.silhouette.auth.AuthEnv

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class RatingController @Inject(ratingRepository: RatingRepositoryInterface, val silhouette: Silhouette[AuthEnv])(
    implicit val ec: ExecutionContext
) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def rate = UserAwareAction.async(parse.json) { implicit request =>
    request.body
      .validate[Rating]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        rating =>
          ratingRepository
            .create(
              rating.copy(id = Some(UUID.randomUUID()), creationDate = Some(OffsetDateTime.now()))
            )
            .map(rating => Ok(Json.toJson(rating)))
      )
  }
}
