package controllers

import com.mohiva.play.silhouette.api.LoginEvent
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import config.EmailConfiguration
import models._
import models.token.TokenKind.ValidateEmail
import orchestrators._
import play.api._
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.user.UserRepositoryInterface
import utils.EmailAddress
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AccountController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    accessesOrchestrator: AccessesOrchestrator,
    emailConfiguration: EmailConfiguration,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  implicit val contactAddress = emailConfiguration.contactAddress
  implicit val ccrfEmailSuffix = emailConfiguration.ccrfEmailSuffix

  def fetchUser = SecuredAction.async { implicit request =>
    for {
      userOpt <- userRepository.get(request.identity.id)
    } yield userOpt
      .map { user =>
        Ok(Json.toJson(user))
      }
      .getOrElse(NotFound)
  }

  def activateAccount = UnsecuredAction.async(parse.json) { implicit request =>
    for {
      activationRequest <- request.parseBody[ActivationRequest]()
      _ <- accessesOrchestrator.handleActivationRequest(activationRequest)
    } yield NoContent

  }
  def sendDGCCRFInvitation = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async(parse.json) {
    implicit request =>
      request.body
        .validate[EmailAddress]((JsPath \ "email").read[EmailAddress])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          email => accessesOrchestrator.sendDGCCRFInvitation(email).map(_ => Ok)
        )
  }
  def fetchPendingDGCCRF = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async { _ =>
    for {
      accessToken <- accessTokenRepository.fetchPendingTokensDGCCRF
    } yield Ok(
      Json.toJson(
        accessToken.map(t =>
          Json.obj(
            "email" -> t.emailedTo,
            "tokenCreation" -> t.creationDate,
            "tokenExpiration" -> t.expirationDate
          )
        )
      )
    )
  }
  def fetchDGCCRFUsers = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async { _ =>
    for {
      users <- userRepository.list(UserRole.DGCCRF)
    } yield Ok(
      Json.toJson(
        users.map(u =>
          Json.obj(
            "email" -> u.email,
            "firstName" -> u.firstName,
            "lastName" -> u.lastName,
            "lastEmailValidation" -> u.lastEmailValidation
          )
        )
      )
    )
  }
  def fetchTokenInfo(token: String) = UnsecuredAction.async { _ =>
    accessesOrchestrator
      .fetchDGCCRFUserActivationToken(token)
      .map(token => Ok(Json.toJson(token)))
  }

  def validateEmail = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[String]((JsPath \ "token").read[String])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        token =>
          for {
            accessToken <- accessTokenRepository.findToken(token)
            oUser <- accessToken
              .filter(_.kind == ValidateEmail)
              .map(accessesOrchestrator.validateEmail)
              .getOrElse(Future(None))
            authToken <- oUser
              .map(user =>
                silhouette.env.authenticatorService
                  .create(LoginInfo(CredentialsProvider.ID, user.email.toString))
                  .flatMap { authenticator =>
                    silhouette.env.eventBus.publish(LoginEvent(user, request))
                    silhouette.env.authenticatorService.init(authenticator).map(Some(_))
                  }
              )
              .getOrElse(Future(None))
          } yield authToken.map(token => Ok(Json.obj("token" -> token, "user" -> oUser.get))).getOrElse(NotFound)
      )
  }

}
