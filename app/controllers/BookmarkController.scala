package controllers

import authentication.Authenticator
import authentication.actions.ImpersonationAction.ForbidImpersonation
import authentication.actions.UserAction.WithRole
import models._
import orchestrators._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import java.util.UUID
import scala.concurrent.ExecutionContext

class BookmarkController(
    bookmarkOrchestrator: BookmarkOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def addBookmark(uuid: UUID) =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).andThen(ForbidImpersonation).async { request =>
      for {
        _ <- bookmarkOrchestrator.addBookmark(uuid, request.identity)
      } yield Ok
    }

  def removeBookmark(uuid: UUID) =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).andThen(ForbidImpersonation).async { request =>
      for {
        _ <- bookmarkOrchestrator.removeBookmark(uuid, request.identity)
      } yield Ok
    }

  def countBookmarks() = SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).async { request =>
    for {
      count <- bookmarkOrchestrator.countBookmarks(request.identity)
    } yield Ok(Json.toJson(count))
  }

}
