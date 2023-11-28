package utils.auth

import controllers.error.AppError.AuthError
import models.User
import models.UserPermission
import models.UserRole
import play.api.mvc.Results.Forbidden
import play.api.mvc.ActionBuilder
import play.api.mvc.ActionFilter
import play.api.mvc.ActionTransformer
import play.api.mvc.AnyContent
import play.api.mvc.BodyParsers
import play.api.mvc.Request
import play.api.mvc.Result
import utils.auth.UserAction.UserRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserAction(val parser: BodyParsers.Default, authenticator: Authenticator[User])(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[UserRequest, AnyContent]
    with ActionTransformer[Request, UserRequest] {
  override protected def transform[A](request: Request[A]): Future[UserRequest[A]] =
    authenticator.authenticate(request).flatMap {
      case Some(user) => Future.successful(IdentifiedRequest(user, request))
      case None       => Future.failed(AuthError("User not found in DB"))
    }
}

object UserAction {
  type UserRequest[A] = IdentifiedRequest[User, A]

  def WithPermission(
      anyOfPermissions: UserPermission.Value*
  )(implicit ec: ExecutionContext): ActionFilter[UserRequest] = new ActionFilter[UserRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] =
      Future.successful {
        if (anyOfPermissions.intersect(request.identity.userRole.permissions).nonEmpty) {
          None
        } else {
          Some(Forbidden)
        }
      }
  }

  def WithRole(anyOfRoles: UserRole*)(implicit ec: ExecutionContext): ActionFilter[UserRequest] =
    new ActionFilter[UserRequest] {
      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] =
        Future.successful {
          if (anyOfRoles.contains(request.identity.userRole)) {
            None
          } else {
            Some(Forbidden)
          }
        }
    }
}
