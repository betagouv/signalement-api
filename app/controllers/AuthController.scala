package controllers

import actors.EmailActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.LoginEvent
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models._
import orchestrators.AccessesOrchestrator
import play.api._
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import repositories.AuthTokenRepository
import repositories.UserRepository
import utils.EmailAddress
import utils.EmailSubjects
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.UserService
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
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
    @Named("email-actor") emailActor: ActorRef,
    configuration: Configuration,
    credentialsProvider: CredentialsProvider
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass())

  implicit val timeout: akka.util.Timeout = 5.seconds
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")
  implicit val dgccrfEmailValidation =
    java.time.Period.parse(configuration.get[String]("play.tokens.dgccrfEmailValidation"))

  def authenticate = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[UserLogin]
      .fold(
        err => Future(BadRequest),
        data =>
          for {
            _ <- userRepository.saveAuthAttempt(data.login)
            attempts <- userRepository.countAuthAttempts(data.login, java.time.Duration.parse("PT30M"))
            response <- if (attempts > 15) Future(Forbidden)
                        else
                          credentialsProvider
                            .authenticate(Credentials(data.login, data.password))
                            .flatMap { loginInfo =>
                              userService.retrieve(loginInfo).flatMap {
                                case Some(user)
                                    if user.userRole == UserRoles.DGCCRF
                                      && user.lastEmailValidation
                                        .exists(_.isBefore(OffsetDateTime.now.minus(dgccrfEmailValidation))) =>
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
        err => Future(BadRequest),
        login =>
          userService.retrieve(LoginInfo(CredentialsProvider.ID, login)).flatMap {
            case Some(user) =>
              for {
                _ <- authTokenRepository.deleteForUserId(user.id)
                authToken <-
                  authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, OffsetDateTime.now.plusDays(1)))
                _ <- sendResetPasswordMail(user, authToken)
              } yield Ok
            case _ =>
              Future.successful(
                Ok
              ) // TODO: renvoyer une erreur? 424 FAILED_DEPENDENCY? 422 UNPROCESSABLE_ENTITY? 412 PRECONDITION_FAILED
          }
      )
  }

  private def sendResetPasswordMail(user: User, authToken: AuthToken) = {
    emailActor ? EmailActor.EmailRequest(
      from = configuration.get[EmailAddress]("play.mail.from"),
      recipients = Seq(user.email),
      subject = EmailSubjects.RESET_PASSWORD,
      bodyHtml = views.html.mails.resetPassword(user, authToken).toString
    )
    logger.debug(s"Sent password reset to ${user.email}")
    Future(())
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
