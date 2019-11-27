package controllers

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.api.{LoginEvent, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models.{AuthToken, User, UserLogin}
import play.api._
import play.api.libs.json.{JsError, JsPath, Json}
import play.api.libs.mailer.AttachmentFile
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
                                environment: Environment,
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


  def forgotPassword = UnsecuredAction.async(parse.json) { implicit request =>

    logger.debug("forgotPassword")

    request.body.validate[String]((JsPath \ "login").read[String]).fold(
      err => Future(BadRequest),
      login =>
        userService.retrieve(LoginInfo(CredentialsProvider.ID, login)).flatMap{
          case Some(user) if user.email.isDefined =>
            for {
              _ <- authTokenRepository.deleteForUserId(user.id)
              authToken <- authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, OffsetDateTime.now.plusDays(1)))
              _ <- sendResetPasswordMail(user, s"${configuration.get[String]("play.website.url")}/connexion/nouveau-mot-de-passe/${authToken.id}")
            } yield {
              Ok
            }
          case _ => Future.successful(Ok) // TODO: renvoyer une erreur? 424 FAILED_DEPENDENCY? 422 UNPROCESSABLE_ENTITY? 412 PRECONDITION_FAILED
        }
    )
  }


  private def sendResetPasswordMail(user: User, url: String) = {
    logger.debug(s"email ${user.email.get}")
    Future(mailerService.sendEmail(
      from = configuration.get[EmailAddress]("play.mail.from"),
      recipients = user.email.get)(
      subject = "Votre mot de passe SignalConso",
      bodyHtml = views.html.mails.resetPassword(user, url).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
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