package authentication.actions

import authentication.Authenticator
import authentication.actions.UserAction.UserRequest
import controllers.error.AppError.AuthError
import controllers.error.AppErrorTransformer
import models.User
import models.UserPermission
import models.UserRole
import play.api.mvc.Results.Forbidden
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserAction(val parser: BodyParsers.Default, authenticator: Authenticator[User])(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[UserRequest, AnyContent]
    with ActionRefiner[Request, UserRequest] {
  override protected def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] =
    authenticator.authenticate(request).flatMap {
      case Right(Some(user)) => Future.successful(Right(IdentifiedRequest(user, request)))
      case Right(None) =>
        val result = AppErrorTransformer.handleError(request, AuthError("User not found in DB"))
        Future.successful(Left(result))
      case Left(error) =>
        val result = AppErrorTransformer.handleError(request, error)
        Future.successful(Left(result))
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
