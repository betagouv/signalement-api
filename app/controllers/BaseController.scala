package controllers

import authentication.actions.UserAction
import controllers.error.AppErrorTransformer.handleError
import play.api.mvc._
import UserAction.UserRequest

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ErrorHandlerActionFunction[R[_] <: play.api.mvc.Request[_]](
    getIdentity: R[_] => Option[UUID] = (_: R[_]) => None
)(implicit
    ec: ExecutionContext
) extends ActionFunction[R, R] {

  def invokeBlock[A](
      request: R[A],
      block: R[A] => Future[Result]
  ): Future[Result] =
    // An error may happen either in the result of the future,
    // or when building the future itself
    Try {
      block(request).recover { case err =>
        handleError(request, err, getIdentity(request))
      }
    } match {
      case Success(res) => res
      case Failure(err) =>
        Future.successful(handleError(request, err, getIdentity(request)))
    }

  override protected def executionContext: ExecutionContext = ec
}

abstract class BaseController(
    override val controllerComponents: ControllerComponents
) extends AbstractController(controllerComponents) {
  implicit val ec: ExecutionContext

  // We should always use our wrappers, to get our error handling
  // We must NOT bind Action to UnsecuredAction as it was before
  // It has not the same bahaviour : UnsecuredAction REJECTS a valid user connected when we just want to allow everyone
  override val Action: ActionBuilder[Request, AnyContent] =
    super.Action andThen new ErrorHandlerActionFunction[Request]()

  def SecuredAction: ActionBuilder[UserRequest, AnyContent] = new UserAction(
    new BodyParsers.Default(controllerComponents.parsers)
  ) andThen new ErrorHandlerActionFunction[UserRequest]()

}
