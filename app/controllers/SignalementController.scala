package controllers

import javax.inject.Inject
import models.Signalement
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.SignalementRepository

import scala.concurrent.{ExecutionContext, Future}

class SignalementController @Inject()(signalementRepository: SignalementRepository)
                                     (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createSignalement = Action.async(parse.json) { implicit request =>

    logger.debug("createSignalement")

    val anomalyResult = request.body.validate[Signalement]

    anomalyResult.fold(
      errors => {
        logger.error("Error createSignalement" + JsError.toJson(errors))
        Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors))))
      },
      signalement =>
        signalementRepository.create(signalement).flatMap(signalement => Future.successful(Ok(Json.toJson(signalement))))
    )
  }

}
