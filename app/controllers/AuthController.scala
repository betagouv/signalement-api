package controllers

import com.mohiva.play.silhouette.api.Silhouette
import orchestrators.AuthOrchestrator
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import utils.silhouette.auth.AuthEnv
import error.AppErrorTransformer.handleError
import models.auth.PasswordChange
import models.auth.UserCredentials
import models.auth.UserLogin
import models.auth.UserPassword
import play.api.mvc.Action

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class AuthController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    authOrchestrator: AuthOrchestrator
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def authenticate: Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    val resultOrError = for {
      userLogin <- request.parseBody[UserCredentials]()
      userSession <- authOrchestrator.login(userLogin, request)
    } yield Ok(Json.toJson(userSession))

    resultOrError.recover { case err => handleError(err) }
  }

  def forgotPassword: Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    val resultOrError = for {
      userLogin <- request.parseBody[UserLogin]()
      _ <- authOrchestrator.forgotPassword(userLogin)
    } yield Ok

    resultOrError.recover { case err => handleError(err) }
  }

  def resetPassword(token: UUID): Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    val resultOrError = for {
      userPassword <- request.parseBody[UserPassword]()
      _ <- authOrchestrator.resetPassword(token, userPassword)
    } yield NoContent

    resultOrError.recover { case err => handleError(err) }
  }

  def changePassword = SecuredAction.async(parse.json) { implicit request =>
    val resultOrError = for {
      updatePassword <- request.parseBody[PasswordChange]()
      _ <- authOrchestrator.changePassword(request.identity, updatePassword)
    } yield NoContent

    resultOrError.recover { case err => handleError(err) }

  }

}
