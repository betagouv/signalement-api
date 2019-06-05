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
                                credentialsProvider: CredentialsProvider
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>

    request.body.validate[UserLogin].fold(
      err => Future(BadRequest),
      data => {
        credentialsProvider.authenticate(Credentials(data.login, data.password)).flatMap { loginInfo =>
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

}