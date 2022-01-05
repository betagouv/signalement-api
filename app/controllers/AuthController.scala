package controllers

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.exceptions.InvalidPasswordException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models._
import orchestrators.AuthOrchestrator
import play.api._
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.mvc.Request
import repositories.AuthTokenRepository
import repositories.UserRepository
import services.Email.ResetPassword
import services.MailService
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.UserService
import error.AppErrorTransformer.handleError
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import error.AppError._

@Singleton
class AuthController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    userRepository: UserRepository,
    authOrchestrator: AuthOrchestrator,
    authTokenRepository: AuthTokenRepository,
    userService: UserService,
    mailService: MailService,
    credentialsProvider: CredentialsProvider
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>
    val resultOrError = for {
      userLogin <- request.parseBody[UserLogin]()
      token <- getToken(userLogin)
      userSession <- authOrchestrator.login(userLogin, token)
    } yield Ok(Json.toJson(userSession))

    resultOrError.recover { case err => handleError(err) }

  }

  private def getToken(userLogin: UserLogin)(implicit req: Request[_]): Future[String] = for {
    loginInfo <- credentialsProvider
      .authenticate(Credentials(userLogin.login, userLogin.password))
      .recoverWith {
        case _: InvalidPasswordException =>
          Future.failed(InvalidPassword(userLogin.login))
        case _: IdentityNotFoundException => Future.failed(UserNotFound(userLogin.login))
        case err =>
          Future.failed(ServerError("Unexpected error when authenticating user", Some(err)))
      }
    authenticator <- silhouette.env.authenticatorService.create(loginInfo)
    token <- silhouette.env.authenticatorService.init(authenticator)
  } yield token

  def forgotPassword = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[String]((JsPath \ "login").read[String])
      .fold(
        err => {
          logger.error(s"Failure parsing UserLogin ${err}")
          Future(BadRequest)
        },
        login =>
          userService.retrieve(LoginInfo(CredentialsProvider.ID, login)).flatMap {
            case Some(user) =>
              for {
                _ <- authTokenRepository.deleteForUserId(user.id)
                authToken <-
                  authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, OffsetDateTime.now.plusDays(1)))
                _ <- mailService.send(ResetPassword(user, authToken))
              } yield Ok
            case _ =>
              Future.successful(
                Ok
              ) // TODO: renvoyer une erreur? 424 FAILED_DEPENDENCY? 422 UNPROCESSABLE_ENTITY? 412 PRECONDITION_FAILED
          }
      )
  }

  def resetPassword(token: UUID) = UnsecuredAction.async(parse.json) { implicit request =>
    authTokenRepository.findValid(token).flatMap {
      case Some(authToken) =>
        request.body
          .validate[String]((JsPath \ "password").read[String])
          .fold(
            errors => Future.successful(BadRequest(JsError.toJson(errors))),
            password =>
              for {
                _ <- userRepository.updatePassword(authToken.userID, password)
                _ <- authTokenRepository.deleteForUserId(authToken.userID)
              } yield NoContent
          )
      case None => Future.successful(NotFound)
    }
  }

}
