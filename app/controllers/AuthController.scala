package controllers

import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models.{PasswordChange, UserLogin}
import play.api._
import play.api.libs.json.{JsError, Json}
import repositories.UserRepository
import utils.silhouette.{AuthEnv, UserService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
                                val silhouette: Silhouette[AuthEnv],
                                userService: UserService,
                                userRepository: UserRepository,
                                credentialsProvider: CredentialsProvider,
                                passwordHasherRegistry: PasswordHasherRegistry
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>

    request.body.validate[UserLogin].fold(
      err => Future(BadRequest),
      data => {
        credentialsProvider.authenticate(Credentials(data.email, data.password)).flatMap { loginInfo =>
          logger.debug("loginInfo");
          userService.retrieve(loginInfo).flatMap {
            case Some(user) => silhouette.env.authenticatorService.create(loginInfo).flatMap { authenticator =>
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              silhouette.env.authenticatorService.init(authenticator).map { token =>
                Ok(Json.obj("token" -> token, "user" -> user))
              }
            }
            case None => {
              logger.debug("None");
              Future(Unauthorized)
            }
          }
        }
      }.recover {
        case e => {
          e.printStackTrace()
          Unauthorized
        }
      }
    )
  }

  def changePassword = SecuredAction.async(parse.json) { implicit request =>

    logger.debug("changePassword")

    request.body.validate[PasswordChange].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      passwordChange =>
        userRepository.updatePassword(request.identity.id.get, passwordHasherRegistry.current.hash(passwordChange.password1).password)
          .flatMap(_ => Future.successful(NoContent))
    )
  }

}