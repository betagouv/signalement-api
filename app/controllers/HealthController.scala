package controllers

import play.api.libs.json.Json
import play.api.mvc.AbstractController
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class HealthController(
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends AbstractController(controllerComponents) {

  def health =
    Action.async(parse.empty) { _ =>
      Future.successful(Ok(Json.obj("name" -> "signalconso-api")))
    }

}
