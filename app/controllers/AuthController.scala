package controllers

import com.mohiva.play.silhouette.api.LoginEvent
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import config.AppConfigLoader
import models._
import orchestrators.AccessesOrchestrator
import play.api._
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import repositories.AuthTokenRepository
import repositories.UserRepository
import services.MailService
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.UserService

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class AuthController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    accessesOrchestrator: AccessesOrchestrator,
    userRepository: UserRepository,
    authTokenRepository: AuthTokenRepository,
    userService: UserService,
    mailService: MailService,
    credentialsProvider: CredentialsProvider,
    appConfigLoader: AppConfigLoader
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[UserLogin]
      .fold(
        err => {
          logger.error(s"Failure parsing UserLogin ${err}")
          Future(BadRequest)
        },
        data =>
          for {
            _ <- userRepository.saveAuthAttempt(data.login)
            attempts <- userRepository.countAuthAttempts(data.login, java.time.Duration.parse("PT30M"))
            response <-
              if (attempts > 15) Future(Forbidden)
              else
                credentialsProvider
                  .authenticate(Credentials(data.login, data.password))
                  .flatMap { loginInfo =>
                    userService.retrieve(loginInfo).flatMap {
                      case Some(user)
                          if user.userRole == UserRole.DGCCRF
                            && user.lastEmailValidation
                              .exists(
                                _.isBefore(
                                  OffsetDateTime.now
                                    .minus(appConfigLoader.get.token.dgccrfDelayBeforeRevalidation)
                                )
                              ) =>
                        accessesOrchestrator.sendEmailValidation(user).map(_ => Locked)
                      case Some(user) =>
                        silhouette.env.authenticatorService.create(loginInfo).flatMap { authenticator =>
                          silhouette.env.eventBus.publish(LoginEvent(user, request))
                          silhouette.env.authenticatorService.init(authenticator).map { token =>
                            Ok(Json.obj("token" -> token, "user" -> user))
                          }
                        }
                      case None => userRepository.saveAuthAttempt(data.login).map(_ => Unauthorized)
                    }
                  }
                  .recoverWith { case e =>
                    logger.error(e.getMessage)
                    Future(Unauthorized)
                  }
          } yield response
      )
  }

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
                _ <- Future(mailService.Common.sendResetPassword(user, authToken))
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
