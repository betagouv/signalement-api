package controllers

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.api.{LoginEvent, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models.{AuthToken, User, UserLogin}
import play.api._
import play.api.libs.json.{JsError, JsPath, Json}
import repositories.{AuthTokenRepository, UserRepository}
import services.MailerService
import utils.silhouette.auth.{AuthEnv, UserService}
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
                                val silhouette: Silhouette[AuthEnv],
                                userRepository: UserRepository,
                                authTokenRepository: AuthTokenRepository,
                                userService: UserService,
                                mailerService: MailerService,
                                configuration: Configuration,
                                credentialsProvider: CredentialsProvider
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>

    request.body.validate[UserLogin].fold(
      err => Future(BadRequest),
      data => {
        credentialsProvider.authenticate(Credentials(data.login, data.password)).flatMap { loginInfo =>
          userService.retrieveSafe(loginInfo).flatMap {
            case Some(user) => silhouette.env.authenticatorService.create(loginInfo).flatMap { authenticator =>
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              silhouette.env.authenticatorService.init(authenticator).map { token =>
                Ok(Json.obj("token" -> token, "user" -> user))
              }
            }
            case None => userRepository.saveAuthAttempt(data.login).map(_ => Unauthorized)
          }
        }
      }.recoverWith {
        case e => {
          logger.error(e.getMessage)
          userRepository.saveAuthAttempt(data.login).map(_ => Unauthorized)
        }
      }
    )
  }


  def forgotPassword = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[String]((JsPath \ "login").read[String]).fold(
      err => Future(BadRequest),
      login =>
        userService.retrieve(LoginInfo(CredentialsProvider.ID, login)).flatMap{
          case Some(user) =>
            for {
              _ <- authTokenRepository.deleteForUserId(user.id)
              authToken <- authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, OffsetDateTime.now.plusDays(1)))
              _ <- sendResetPasswordMail(user, authToken)
            } yield {
              Ok
            }
          case _ => Future.successful(Ok) // TODO: renvoyer une erreur? 424 FAILED_DEPENDENCY? 422 UNPROCESSABLE_ENTITY? 412 PRECONDITION_FAILED
        }
    )
  }


  private def sendResetPasswordMail(user: User, authToken: AuthToken) = {
    mailerService.sendEmail(
      from = configuration.get[EmailAddress]("play.mail.from"),
      recipients = user.email)(
      subject = "Votre mot de passe SignalConso",
      bodyHtml = views.html.mails.resetPassword(user, authToken).toString
    )
    logger.debug(s"Sent password reset to ${user.email}")
    Future(Unit)
  }

  def resetPassword(token: UUID) = UnsecuredAction.async(parse.json) { implicit request =>

    authTokenRepository.findValid(token).flatMap {
      case Some(authToken) =>
        request.body.validate[String]((JsPath \ "password").read[String]).fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          password => for {
            _ <- userRepository.updatePassword(authToken.userID, password)
            _ <- authTokenRepository.deleteForUserId(authToken.userID)
          } yield {
            NoContent
          }
        )
      case None => Future.successful(NotFound)
    }
  }

}