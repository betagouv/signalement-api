package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.email.ValidateEmailCode
import models.email.ValidateEmail
import orchestrators.EmailValidationOrchestrator
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import utils.silhouette.auth.AuthEnv

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import error.AppErrorTransformer.handleError
import play.api.mvc.Action
import play.api.mvc.Result

@Singleton
class EmailValidationController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    emailValidationOrchestrator: EmailValidationOrchestrator
)(implicit
    ec: ExecutionContext
) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def checkEmail(): Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    logger.debug("Calling checking email API")
    val validationResultOrError: Future[Result] = for {
      validateEmail <- request.parseBody[ValidateEmail]()
      validationResult <- emailValidationOrchestrator.checkEmail(validateEmail.email)
    } yield Ok(Json.toJson(validationResult))

    validationResultOrError.recover { case err => handleError(err) }
  }

  def validEmail(): Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    logger.debug("Calling validate email API")

    val validationResultOrError: Future[Result] = for {
      validateEmailCode <- request.parseBody[ValidateEmailCode]()
      validationResult <- emailValidationOrchestrator.validateEmailCode(validateEmailCode)
    } yield Ok(Json.toJson(validationResult))

    validationResultOrError.recover { case err => handleError(err) }
  }
}
