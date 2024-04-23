package authentication.actions

import authentication.actions.UserAction.UserRequest
import controllers.error.AppError.AuthError
import controllers.error.AppErrorTransformer
import models.User
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserAction(val parser: BodyParsers.Default)(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[UserRequest, AnyContent]
    with ActionRefiner[Request, UserRequest] {
  override protected def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] = {
    val result = AppErrorTransformer.handleError(request, AuthError("User not found in DB"))
    Future.successful(Left(result))
  }
}

object UserAction {
  type UserRequest[A] = IdentifiedRequest[User, A]

}
