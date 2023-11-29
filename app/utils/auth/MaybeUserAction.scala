package utils.auth

import models.User
import play.api.mvc.ActionBuilder
import play.api.mvc.ActionTransformer
import play.api.mvc.AnyContent
import play.api.mvc.BodyParsers
import play.api.mvc.Request
import utils.auth.MaybeUserAction.MaybeUserRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MaybeUserAction(val parser: BodyParsers.Default, authenticator: Authenticator[User])(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[MaybeUserRequest, AnyContent]
    with ActionTransformer[Request, MaybeUserRequest] {
  override protected def transform[A](request: Request[A]): Future[MaybeUserRequest[A]] =
    authenticator
      .authenticate(request)
      .map {
        case Right(maybeUser) => IdentifiedRequest(maybeUser, request)
        case Left(_)          => IdentifiedRequest(None, request)
      }
}

object MaybeUserAction {
  type MaybeUserRequest[A] = IdentifiedRequest[Option[User], A]
}
