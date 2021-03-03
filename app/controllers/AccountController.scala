package controllers

import java.net.URI
import com.mohiva.play.silhouette.api.{Silhouette, LoginEvent, LoginInfo}
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models._
import orchestrators._
import play.api._
import play.api.libs.json.{JsError, JsPath, Json}
import repositories._
import utils.Constants.{ActionEvent, ReportStatus}
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.JsonValidationError

@Singleton
class AccountController @Inject()(
                                   val silhouette: Silhouette[AuthEnv],
                                   userRepository: UserRepository,
                                   companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   accessesOrchestrator: AccessesOrchestrator,
                                   reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   credentialsProvider: CredentialsProvider,
                                   configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")
  implicit val ccrfEmailSuffix = configuration.get[String]("play.mail.ccrfEmailSuffix")

  def changePassword = SecuredAction.async(parse.json) { implicit request =>
    request.body.validate[PasswordChange].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      passwordChange => {
        for {
          identLogin <- credentialsProvider.authenticate(Credentials(request.identity.email.value, passwordChange.oldPassword))
          _ <- userRepository.updatePassword(request.identity.id, passwordChange.newPassword)
        } yield {
          NoContent
        }
      }.recover {
        case e => {
          Unauthorized
        }
      }
    )
  }

  def activateAccount = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[ActivationRequest].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      {
        case ActivationRequest(draftUser, token, companySiret) =>
          accessesOrchestrator
            .handleActivationRequest(draftUser, token, companySiret)
            .map {
              case accessesOrchestrator.ActivationOutcome.NotFound      => NotFound
              case accessesOrchestrator.ActivationOutcome.EmailConflict => Conflict  // HTTP 409
              case accessesOrchestrator.ActivationOutcome.Success       => NoContent
            }
      }
    )
  }
  def sendDGCCRFInvitation = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async(parse.json) { implicit request =>
    request.body.validate[EmailAddress]((JsPath \ "email").read[EmailAddress]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      email => if (email.value.endsWith(ccrfEmailSuffix))
                  accessesOrchestrator.sendDGCCRFInvitation(email).map(_ => Ok)
               else Future(Forbidden(s"Email invalide. Email acceptés : *${ccrfEmailSuffix}"))
    )
  }
  def fetchPendingDGCCRF = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async { implicit request =>
    for {
      accessToken <- accessTokenRepository.fetchPendingTokensDGCCRF
    } yield Ok(Json.toJson(accessToken.map(t =>
      Json.obj(
        "email" -> t.emailedTo,
        "tokenCreation" -> t.creationDate,
        "tokenExpiration" -> t.expirationDate
      ))
    ))
  }
  def fetchDGCCRFUsers = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async { implicit request =>
    for {
      users <- userRepository.list(UserRoles.DGCCRF)
    } yield Ok(Json.toJson(users.map(u =>
      Json.obj(
        "email" -> u.email,
        "firstName" -> u.firstName,
        "lastName" -> u.lastName
      ))
    ))
  }
  def fetchTokenInfo(token: String) = UnsecuredAction.async { implicit request =>
    for {
      accessToken <- accessTokenRepository.findToken(token)
    } yield accessToken.map(t =>
      Ok(Json.toJson(TokenInfo(t.token, t.kind, None, t.emailedTo)))
    ).getOrElse(NotFound)
  }
  def validateEmail = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[String]((JsPath \ "token").read[String]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      token =>
        for {
          accessToken <- accessTokenRepository.findToken(token)
          oUser       <- accessToken.filter(_.kind == TokenKind.VALIDATE_EMAIL)
                                    .map(accessesOrchestrator.validateEmail(_)).getOrElse(Future(None))
          authToken   <- oUser.map(user =>
            silhouette.env.authenticatorService.create(LoginInfo(CredentialsProvider.ID, user.email.toString)).flatMap { authenticator =>
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              silhouette.env.authenticatorService.init(authenticator).map(Some(_))
            }
          ).getOrElse(Future(None))
        } yield authToken.map(token => Ok(Json.obj("token" -> token, "user" -> oUser.get))).getOrElse(NotFound)
    )
  }
}
