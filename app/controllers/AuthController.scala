package controllers

import models.UserRole
import orchestrators.AuthOrchestrator
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.auth.PasswordChange
import models.auth.UserCredentials
import models.auth.UserLogin
import models.auth.UserPassword
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import utils.auth.UserAction.WithRole
import utils.auth.CookieAuthenticator

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AuthController(
    authOrchestrator: AuthOrchestrator,
    authenticator: CookieAuthenticator,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def authenticate: Action[JsValue] = Action.async(parse.json) { implicit request =>
    for {
      userLogin   <- request.parseBody[UserCredentials]()
      userSession <- authOrchestrator.login(userLogin, request)
    } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
  }

  def logout(): Action[AnyContent] = SecuredAction { _ =>
    authenticator.discard(NoContent)
  }

  def getUser(): Action[AnyContent] = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(request.identity)))
  }

  def listAuthAttempts(login: Option[String]) =
    SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.empty) { _ =>
      authOrchestrator
        .listAuthenticationAttempts(login)
        .map(authAttempts => Ok(Json.toJson(authAttempts)))
    }

  def forgotPassword: Action[JsValue] = Action.async(parse.json) { implicit request =>
    for {
      userLogin <- request.parseBody[UserLogin]()
      _         <- authOrchestrator.forgotPassword(userLogin)
    } yield Ok
  }

  def resetPassword(token: UUID): Action[JsValue] = Action.async(parse.json) { implicit request =>
    for {
      userPassword <- request.parseBody[UserPassword]()
      _            <- authOrchestrator.resetPassword(token, userPassword)
    } yield NoContent
  }

  def changePassword = SecuredAction.async(parse.json) { implicit request =>
    for {
      updatePassword <- request.parseBody[PasswordChange]()
      _              <- authOrchestrator.changePassword(request.identity, updatePassword)
    } yield NoContent

  }

}
