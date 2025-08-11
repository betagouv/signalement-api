package controllers

import authentication.Authenticator
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
    Act.secured.adminsAndReadonlyAndAgentsWithSSMVM.forbidImpersonation.async { request =>
      for {
        _ <- bookmarkOrchestrator.addBookmark(uuid, request.identity)
      } yield Ok
    }

  def removeBookmark(uuid: UUID) =
    Act.secured.adminsAndReadonlyAndAgentsWithSSMVM.forbidImpersonation.async { request =>
      for {
        _ <- bookmarkOrchestrator.removeBookmark(uuid, request.identity)
      } yield Ok
    }

  def countBookmarks() = Act.secured.adminsAndReadonlyAndAgentsWithSSMVM.allowImpersonation.async { request =>
    for {
      count <- bookmarkOrchestrator.countBookmarks(request.identity)
    } yield Ok(Json.toJson(count))
  }

}
