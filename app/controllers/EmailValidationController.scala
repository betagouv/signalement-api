package controllers

import models.email.ValidateEmail
import models.email.ValidateEmailCode
import models.EmailValidationFilter
import models.PaginatedSearch
import models.User
import orchestrators.EmailValidationOrchestrator
import play.api._
import _root_.controllers.error.AppError.MalformedQueryParams
import authentication.Authenticator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import models.PaginatedResult.paginatedResultWrites

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailValidationController(
    authenticator: Authenticator[User],
    emailValidationOrchestrator: EmailValidationOrchestrator,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def checkIsEmailValidAndMaybeSendEmail(): Action[JsValue] = Act.public.standardLimit.async(parse.json) {
    implicit request =>
      logger.debug("Calling checking email API")
      for {
        validateEmail <- request.parseBody[ValidateEmail]()
        validationResult <- emailValidationOrchestrator.checkEmail(
          validateEmail.email,
          validateEmail.lang
        )
      } yield Ok(Json.toJson(validationResult))
  }

  def checkEmailCodeAndValidateEmail(): Action[JsValue] = Act.public.standardLimit.async(parse.json) {
    implicit request =>
      logger.debug("Calling validate email API")
      for {
        validateEmailCode <- request.parseBody[ValidateEmailCode]()
        validationResult  <- emailValidationOrchestrator.checkCodeAndValidateEmail(validateEmailCode)
      } yield Ok(Json.toJson(validationResult))
  }

  def searchEmailValidations() = Act.secured.adminsAndReadonly.async { implicit request =>
    EmailValidationFilter
      .fromQueryString(request.queryString)
      .flatMap(filters => PaginatedSearch.fromQueryString(request.queryString).map((filters, _)))
      .fold(
        error => {
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.failed(MalformedQueryParams)
        },
        filters =>
          for {
            res <- emailValidationOrchestrator.search(filters._1, filters._2)
          } yield Ok(Json.toJson(res)(paginatedResultWrites))
      )
  }

  def forceValidateEmail(): Action[JsValue] =
    Act.secured.admins.async(parse.json) { implicit request =>
      logger.debug("Calling validate email API")
      for {
        body             <- request.parseBody[ValidateEmail]()
        validationResult <- emailValidationOrchestrator.validateEmail(body.email)
      } yield Ok(Json.toJson(validationResult))
    }
}
