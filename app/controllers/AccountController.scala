package controllers

import authentication.CookieAuthenticator
import cats.implicits.catsSyntaxOption
import config.EmailConfiguration
import models._
import orchestrators._
import play.api._
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.user.UserRepositoryInterface
import utils.EmailAddress
import error.AppError.MalformedFileKey
import authentication.actions.UserAction.WithRole

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source

class AccountController(
    userOrchestrator: UserOrchestrator,
    userRepository: UserRepositoryInterface,
    accessesOrchestrator: AccessesOrchestrator,
    proAccessTokenOrchestrator: ProAccessTokenOrchestrator,
    emailConfiguration: EmailConfiguration,
    authenticator: CookieAuthenticator,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  implicit val contactAddress: EmailAddress = emailConfiguration.contactAddress

  def fetchUser = SecuredAction.async { implicit request =>
    for {
      userOpt <- userRepository.get(request.identity.id)
    } yield userOpt
      .map { user =>
        Ok(Json.toJson(user))
      }
      .getOrElse(NotFound)
  }

  def activateAccount = IpRateLimitedAction2.async(parse.json) { implicit request =>
    for {
      activationRequest <- request.parseBody[ActivationRequest]()
      createdUser <- activationRequest.companySiret match {
        case Some(siret) =>
          proAccessTokenOrchestrator.activateProUser(activationRequest.draftUser, activationRequest.token, siret)
        case None =>
          accessesOrchestrator.activateAdminOrAgentUser(activationRequest.draftUser, activationRequest.token)
      }
      cookie <- authenticator.init(createdUser.email) match {
        case Right(value) => Future.successful(value)
        case Left(error)  => Future.failed(error)
      }
    } yield authenticator.embed(cookie, Ok(Json.toJson(createdUser)))

  }

  def sendAgentInvitation(role: UserRole) =
    SecuredAction.andThen(WithRole(UserRole.Admins)).async(parse.json) { implicit request =>
      role match {
        case UserRole.DGCCRF =>
          request
            .parseBody[EmailAddress](JsPath \ "email")
            .flatMap(email => accessesOrchestrator.sendDGCCRFInvitation(email).map(_ => Ok))
        case UserRole.DGAL =>
          request
            .parseBody[EmailAddress](JsPath \ "email")
            .flatMap(email => accessesOrchestrator.sendDGALInvitation(email).map(_ => Ok))
        case _ => Future.failed(error.AppError.WrongUserRole(role))
      }
    }

  def sendAgentsInvitations(role: UserRole) =
    SecuredAction.andThen(WithRole(UserRole.Admins)).async(parse.multipartFormData) { implicit request =>
      for {
        filePart <- request.body.file("emails").liftTo[Future](MalformedFileKey("emails"))
        source = Source.fromFile(filePart.ref.path.toFile)
        lines  = source.getLines().toList
        _      = source.close()
        _ <- accessesOrchestrator.sendAgentsInvitations(role, lines)
      } yield Ok
    }

  def sendAdminInvitation =
    SecuredAction.andThen(WithRole(UserRole.SuperAdmin)).async(parse.json) { implicit request =>
      request
        .parseBody[EmailAddress](JsPath \ "email")
        .flatMap(email => accessesOrchestrator.sendAdminInvitation(email).map(_ => Ok))
    }

  def fetchPendingAgent(role: Option[UserRole]) =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnly)).async { request =>
      role match {
        case Some(UserRole.DGCCRF) | Some(UserRole.DGAL) | None =>
          accessesOrchestrator
            .listAgentPendingTokens(request.identity, role)
            .map(tokens => Ok(Json.toJson(tokens)))
        case Some(role) => Future.failed(error.AppError.WrongUserRole(role))
      }
    }

  def fetchAgentUsers =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnly)).async { _ =>
      for {
        users <- userRepository.listForRoles(Seq(UserRole.DGCCRF, UserRole.DGAL))
      } yield Ok(Json.toJson(users))
    }

  def fetchAdminUsers =
    SecuredAction.andThen(WithRole(UserRole.SuperAdmin)).async { _ =>
      for {
        users <- userRepository.listForRoles(Seq(UserRole.SuperAdmin, UserRole.Admin, UserRole.ReadOnlyAdmin))
      } yield Ok(Json.toJson(users))
    }

  // This data is not displayed anywhere
  // The endpoint might be useful to debug without accessing the prod DB
  def fetchAllSoftDeletedUsers = SecuredAction.andThen(WithRole(UserRole.SuperAdmin)).async { _ =>
    for {
      users <- userRepository.listDeleted()
    } yield Ok(Json.toJson(users))
  }

  def fetchTokenInfo(token: String) = IpRateLimitedAction2.async { _ =>
    accessesOrchestrator
      .fetchDGCCRFUserActivationToken(token)
      .map(token => Ok(Json.toJson(token)))
  }

  def validateEmail() = IpRateLimitedAction2.async(parse.json) { implicit request =>
    for {
      token <- request.parseBody[String](JsPath \ "token")
      user  <- accessesOrchestrator.validateAgentEmail(token)
      cookie <- authenticator.init(user.email) match {
        case Right(value) => Future.successful(value)
        case Left(error)  => Future.failed(error)
      }
    } yield authenticator.embed(cookie, Ok(Json.toJson(user)))
  }

  def forceValidateEmail(email: String) =
    SecuredAction.andThen(WithRole(UserRole.Admins)).async { _ =>
      accessesOrchestrator.resetLastEmailValidation(EmailAddress(email)).map(_ => NoContent)
    }

  def edit() = SecuredAction.async(parse.json) { implicit request =>
    for {
      userUpdate     <- request.parseBody[UserUpdate]()
      updatedUserOpt <- userOrchestrator.edit(request.identity.id, userUpdate)
    } yield updatedUserOpt match {
      case Some(updatedUser) => Ok(Json.toJson(updatedUser))
      case _                 => NotFound
    }
  }

  def sendEmailAddressUpdateValidation() = SecuredAction.async(parse.json) { implicit request =>
    for {
      emailAddress <- request.parseBody[EmailAddress](JsPath \ "email")
      _            <- accessesOrchestrator.sendEmailAddressUpdateValidation(request.identity, emailAddress)
    } yield NoContent
  }

  def updateEmailAddress(token: String) = SecuredAction.async { implicit request =>
    for {
      updatedUser <- accessesOrchestrator.updateEmailAddress(request.identity, token)
      cookie <- authenticator.init(updatedUser.email) match {
        case Right(value) => Future.successful(value)
        case Left(error)  => Future.failed(error)
      }
    } yield authenticator.embed(cookie, Ok(Json.toJson(updatedUser)))
  }

  def softDelete(id: UUID) =
    SecuredAction.andThen(WithRole(UserRole.Admins)).async { request =>
      userOrchestrator.softDelete(targetUserId = id, currentUserId = request.identity.id).map(_ => NoContent)
    }

}
