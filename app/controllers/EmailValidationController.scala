package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.email.ValidateEmailCode
import models.email.ValidateEmail
import orchestrators.EmailValidationOrchestrator
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import play.api.mvc.Action
import play.api.mvc.ControllerComponents

class EmailValidationController(
    val silhouette: Silhouette[AuthEnv],
    emailValidationOrchestrator: EmailValidationOrchestrator,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def checkEmail(): Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    logger.debug("Calling checking email API")
    for {
      validateEmail <- request.parseBody[ValidateEmail]()
      validationResult <- emailValidationOrchestrator.checkEmail(validateEmail.email)
    } yield Ok(Json.toJson(validationResult))
  }

  def validEmail(): Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    logger.debug("Calling validate email API")

    for {
      validateEmailCode <- request.parseBody[ValidateEmailCode]()
      validationResult <- emailValidationOrchestrator.validateEmailCode(validateEmailCode)
    } yield Ok(Json.toJson(validationResult))

  }
}
