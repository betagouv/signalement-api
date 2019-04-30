package controllers

import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models.{User, UserLogin}
import play.api._
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsError, Json, Writes}
import repositories.UserRepository
import utils.silhouette.{AuthEnv, UserService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
        logger.debug(s"${data.password}")
        logger.debug(s"${passwordHasherRegistry.current.hash("test").password}")

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

}