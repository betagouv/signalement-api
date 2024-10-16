package controllers

import authentication.CookieAuthenticator
import models.UserRole
import orchestrators.AuthOrchestrator
import play.api._
import play.api.libs.json.JsPath
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.auth.PasswordChange
import models.auth.UserCredentials
import models.auth.UserLogin
import models.auth.UserPassword
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import authentication.actions.UserAction.WithRole
import utils.EmailAddress

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AuthController(
    authOrchestrator: AuthOrchestrator,
    authenticator: CookieAuthenticator,
    controllerComponents: ControllerComponents,
    enableRateLimit: Boolean
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents, enableRateLimit) {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  def authenticate: Action[JsValue] = IpRateLimitedAction2.async(parse.json) { implicit request =>
    for {
      userLogin   <- request.parseBody[UserCredentials]()
      userSession <- authOrchestrator.login(userLogin)
    } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
  }

  def logAs() = SecuredAction.andThen(WithRole(UserRole.SuperAdmin)).async(parse.json) { implicit request =>
    for {
      userEmail   <- request.parseBody[EmailAddress](JsPath \ "email")
      userSession <- authOrchestrator.logAs(userEmail, request)
    } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
  }

  def logout(): Action[AnyContent] = SecuredAction.async { implicit request =>
    request.identity.impersonator match {
      case Some(impersonator) =>
        authOrchestrator
          .logoutAs(impersonator)
          .map(userSession => authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user))))
      case None => Future.successful(authenticator.discard(NoContent))
    }
  }

  def getUser(): Action[AnyContent] = SecuredAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(request.identity)))
  }

  def listAuthAttempts(login: Option[String]) =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnly)).async(parse.empty) { _ =>
      authOrchestrator
        .listAuthenticationAttempts(login)
        .map(authAttempts => Ok(Json.toJson(authAttempts)))
    }

  def forgotPassword: Action[JsValue] = IpRateLimitedAction2.async(parse.json) { implicit request =>
    for {
      userLogin <- request.parseBody[UserLogin]()
      _         <- authOrchestrator.forgotPassword(userLogin)
    } yield Ok
  }

  def resetPassword(token: UUID): Action[JsValue] = IpRateLimitedAction2.async(parse.json) { implicit request =>
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
