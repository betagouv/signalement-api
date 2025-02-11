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

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source
import cats.syntax.either._

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

  def activateAccount = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      activationRequest <- request.parseBody[ActivationRequest]()
      createdUser <- activationRequest.companySiret match {
        case Some(siret) =>
          proAccessTokenOrchestrator.activateProUser(activationRequest.draftUser, activationRequest.token, siret)
        case None =>
          accessesOrchestrator.activateAdminOrAgentUser(activationRequest.draftUser, activationRequest.token)
      }
      cookie <- authenticator.initSignalConsoCookie(createdUser.email, None).liftTo[Future]
    } yield authenticator.embed(cookie, Ok(Json.toJson(createdUser)))

  }

  def sendAgentInvitation(role: UserRole) =
    Act.secured.admins.async(parse.json) { implicit request =>
      role match {
        case UserRole.DGCCRF =>
          request
            .parseBody[InvitationRequest]()
            .flatMap { invitationRequest =>
              accessesOrchestrator.sendDGCCRFInvitation(invitationRequest).map(_ => Ok)
            }
        case UserRole.DGAL =>
          request
            .parseBody[InvitationRequest]()
            .flatMap(invitationRequest => accessesOrchestrator.sendDGALInvitation(invitationRequest.email).map(_ => Ok))
        case _ => Future.failed(error.AppError.WrongUserRole(role))
      }
    }

  def sendAgentsInvitations(role: UserRole) =
    Act.secured.admins.async(parse.multipartFormData) { implicit request =>
      for {
        filePart <- request.body.file("emails").liftTo[Future](MalformedFileKey("emails"))
        source = Source.fromFile(filePart.ref.path.toFile)
        lines  = source.getLines().toList
        _      = source.close()
        _ <- accessesOrchestrator.sendAgentsInvitations(role, lines)
      } yield Ok
    }

  def sendAdminInvitation(role: UserRole) =
    Act.secured.superAdmins.async(parse.json) { implicit request =>
      role match {
        case UserRole.SuperAdmin =>
          request
            .parseBody[EmailAddress](JsPath \ "email")
            .flatMap(email => accessesOrchestrator.sendSuperAdminInvitation(email).map(_ => Ok))
        case UserRole.Admin =>
          request
            .parseBody[EmailAddress](JsPath \ "email")
            .flatMap(email => accessesOrchestrator.sendAdminInvitation(email).map(_ => Ok))
        case UserRole.ReadOnlyAdmin =>
          request
            .parseBody[EmailAddress](JsPath \ "email")
            .flatMap(email => accessesOrchestrator.sendReadOnlyAdminInvitation(email).map(_ => Ok))
        case _ => Future.failed(error.AppError.WrongUserRole(role))
      }
    }

  def fetchPendingAgent(role: Option[UserRole]) =
    Act.secured.adminsAndReadonly.async { _ =>
      role match {
        case Some(UserRole.DGCCRF) | Some(UserRole.DGAL) | None =>
          accessesOrchestrator
            .listAgentPendingTokens(role)
            .map(tokens => Ok(Json.toJson(tokens)))
        case Some(role) => Future.failed(error.AppError.WrongUserRole(role))
      }
    }

  def fetchAgentUsers =
    Act.secured.adminsAndReadonly.async { _ =>
      for {
        users <- userRepository.listForRoles(Seq(UserRole.DGCCRF, UserRole.DGAL))
      } yield Ok(Json.toJson(users))
    }

  def fetchAdminUsers =
    Act.secured.superAdmins.async { _ =>
      for {
        users <- userRepository.listForRoles(Seq(UserRole.SuperAdmin, UserRole.Admin, UserRole.ReadOnlyAdmin))
      } yield Ok(Json.toJson(users))
    }

  // This data is not displayed anywhere
  // The endpoint might be useful to debug without accessing the prod DB
  def fetchAllSoftDeletedUsers = Act.secured.superAdmins.async { _ =>
    for {
      users <- userRepository.listDeleted()
    } yield Ok(Json.toJson(users))
  }

  def fetchTokenInfo(token: String) = Act.public.standardLimit.async { _ =>
    accessesOrchestrator
      .fetchDGCCRFUserActivationToken(token)
      .map(token => Ok(Json.toJson(token)))
  }

  def validateEmail() = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      token  <- request.parseBody[String](JsPath \ "token")
      user   <- accessesOrchestrator.validateAgentEmail(token)
      cookie <- authenticator.initSignalConsoCookie(user.email, None).liftTo[Future]
    } yield authenticator.embed(cookie, Ok(Json.toJson(user)))
  }

  def forceValidateEmail(email: String) =
    Act.secured.admins.async { _ =>
      accessesOrchestrator.resetLastEmailValidation(EmailAddress(email)).map(_ => NoContent)
    }

  def edit() =
    Act.secured.restrictByProvider.signalConso.forbidImpersonation.async(parse.json) { implicit request =>
      for {
        userUpdate     <- request.parseBody[UserUpdate]()
        updatedUserOpt <- userOrchestrator.edit(request.identity.id, userUpdate)
      } yield updatedUserOpt match {
        case Some(updatedUser) => Ok(Json.toJson(updatedUser))
        case _                 => NotFound
      }
    }

  def sendEmailAddressUpdateValidation() =
    Act.secured.all.forbidImpersonation.async(parse.json) { implicit request =>
      for {
        emailAddress <- request.parseBody[EmailAddress](JsPath \ "email")
        _            <- accessesOrchestrator.sendEmailAddressUpdateValidation(request.identity, emailAddress)
      } yield NoContent
    }

  def updateEmailAddress(token: String) =
    Act.secured.restrictByProvider.signalConso.forbidImpersonation.async { implicit request =>
      for {
        updatedUser <- accessesOrchestrator.updateEmailAddress(request.identity, token)
        cookie      <- authenticator.initSignalConsoCookie(updatedUser.email, None).liftTo[Future]
      } yield authenticator.embed(cookie, Ok(Json.toJson(updatedUser)))
    }

  def softDelete(id: UUID) =
    Act.secured.admins.async { request =>
      userOrchestrator.softDelete(targetUserId = id, currentUserId = request.identity.id).map(_ => NoContent)
    }

}
