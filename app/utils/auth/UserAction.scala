package utils.auth

import controllers.error.AppError.AuthError
import models.{UserPermission, UserRole}
import play.api.mvc.Results.Forbidden
import play.api.mvc.{ActionBuilder, ActionFilter, ActionTransformer, AnyContent, BodyParsers, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

class UserAction(val parser: BodyParsers.Default, authenticator: Authenticator)(implicit val executionContext: ExecutionContext) extends ActionBuilder[UserRequest, AnyContent]
  with ActionTransformer[Request, UserRequest] {
  override protected def transform[A](request: Request[A]): Future[UserRequest[A]] =
    authenticator.authenticate(request).flatMap {
      case Some(user) => Future.successful(UserRequest(user, request))
      case None => Future.failed(AuthError("User not found in DB"))
    }
}

object UserAction {
  def WithPermission(anyOfPermissions: UserPermission.Value*)(implicit ec: ExecutionContext): ActionFilter[UserRequest] = new ActionFilter[UserRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] = {
      Future.successful {
        if (anyOfPermissions.intersect(request.user.userRole.permissions).nonEmpty) {
          None
        } else {
          Some(Forbidden)
        }
      }
    }
  }

  def WithRole(anyOfRoles: UserRole*)(implicit ec: ExecutionContext): ActionFilter[UserRequest] = new ActionFilter[UserRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] = {
      Future.successful {
        if (anyOfRoles.contains(request.user.userRole)) {
          None
        } else {
          Some(Forbidden)
        }
      }
    }
  }
}