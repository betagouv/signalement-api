package controllers

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.Rating
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.RatingRepository
import utils.silhouette.auth.AuthEnv

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class RatingController @Inject()(ratingRepository: RatingRepository,
                                 val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def rate = UserAwareAction.async(parse.json) { implicit request =>

    logger.debug("rate")

    request.body.validate[Rating].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      rating => {
        ratingRepository.createRating(
          rating.copy(
            id = Some(UUID.randomUUID()),
            creationDate = Some(OffsetDateTime.now()))
        ).flatMap(rating => Future.successful(Ok(Json.toJson(rating))))
      }
    )
  }
}
