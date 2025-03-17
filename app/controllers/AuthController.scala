package controllers

import authentication.CookieAuthenticator
import models.PaginatedResult
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
import cats.implicits.catsSyntaxOption
import cats.implicits.toFunctorOps
import orchestrators.proconnect.ProConnectOrchestrator
import utils.EmailAddress
import cats.syntax.either._
import _root_.controllers.error.AppError._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AuthController(
    authOrchestrator: AuthOrchestrator,
    authenticator: CookieAuthenticator,
    controllerComponents: ControllerComponents,
    enableRateLimit: Boolean,
    proConnectOrchestrator: ProConnectOrchestrator
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents, enableRateLimit) {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  def authenticate: Action[JsValue] = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      userLogin   <- request.parseBodyPrivate[UserCredentials]()
      userSession <- authOrchestrator.signalConsoLogin(userLogin)
    } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
  }

  def startProConnectAuthentication(state: String, nonce: String) =
    Act.public.standardLimit.async(parse.empty) { _ =>
      proConnectOrchestrator.saveState(state, nonce).as(NoContent)
    }

  def proConnectAuthenticate(code: String, state: String) =
    Act.public.standardLimit.async(parse.empty) { _ =>
      for {
        (token_id, user) <- proConnectOrchestrator.login(code, state)
        userSession      <- authOrchestrator.proConnectLogin(user, token_id, state)
      } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
    }

  def logAs() = Act.secured.admins.async(parse.json) { implicit request =>
    for {
      userEmail   <- request.parseBody[EmailAddress](JsPath \ "email")
      userSession <- authOrchestrator.logAs(userEmail, request)
    } yield authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user)))
  }

  def logout(): Action[AnyContent] = Act.secured.restrictByProvider.signalConso.allowImpersonation.async {
    implicit request =>
      request.identity.impersonator match {
        case Some(impersonator) =>
          authOrchestrator
            .logoutAs(impersonator)
            .map(userSession => authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user))))
        case None =>
          Future.successful(authenticator.discard(NoContent))
      }
  }

  def logoutProConnect(): Action[AnyContent] =
    Act.secured.restrictByProvider.proConnect.allowImpersonation.async { implicit request =>
      request.identity.impersonator match {
        case Some(impersonator) =>
          authOrchestrator
            .logoutAs(impersonator)
            .map(userSession => authenticator.embed(userSession.cookie, Ok(Json.toJson(userSession.user))))
        case None =>
          for {
            cookiesInfo <- authenticator.extract(request).liftTo[Future]
            tokenId     <- cookiesInfo.proConnectIdToken.liftTo[Future](MissingProConnectTokenId)
            state       <- cookiesInfo.proConnectState.liftTo[Future](MissingProConnectState)
            redirectUrl <- proConnectOrchestrator.endSessionUrl(tokenId, state)
            result = Ok(redirectUrl)
          } yield authenticator.discard(result)
      }
    }

  def getUser(): Action[AnyContent] = Act.secured.all.allowImpersonation.async { implicit request =>
    Future.successful(Ok(Json.toJson(request.identity)))
  }

  def listAuthAttempts(login: Option[String], offset: Option[Long], limit: Option[Int]) =
    Act.secured.adminsAndReadonly.async(parse.empty) { _ =>
      authOrchestrator
        .listAuthenticationAttempts(login, offset, limit)
        .map(authAttempts => Ok(Json.toJson(authAttempts)(PaginatedResult.paginatedResultWrites)))
    }

  def forgotPassword: Action[JsValue] = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      userLogin <- request.parseBody[UserLogin]()
      _         <- authOrchestrator.forgotPassword(userLogin)
    } yield Ok
  }

  def resetPassword(token: UUID): Action[JsValue] =
    Act.public.standardLimit.async(parse.json) { implicit request =>
      for {
        userPassword <- request.parseBodyPrivate[UserPassword]()
        _            <- authOrchestrator.resetPassword(token, userPassword)
      } yield NoContent
    }

  def changePassword =
    Act.secured.restrictByProvider.signalConso.forbidImpersonation.async(parse.json) { implicit request =>
      for {
        updatePassword <- request.parseBodyPrivate[PasswordChange]()
        _              <- authOrchestrator.changePassword(request.identity, updatePassword)
      } yield NoContent

    }

}
