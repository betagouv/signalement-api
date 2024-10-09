package authentication.actions

import authentication.Authenticator
import authentication.actions.ConsumerAction.ConsumerRequest
import controllers.error.AppError.BrokenAuthError
import controllers.error.AppErrorTransformer
import models.Consumer
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ConsumerAction(val parser: BodyParsers.Default, authenticator: Authenticator[Consumer])(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[ConsumerRequest, AnyContent]
    with ActionRefiner[Request, ConsumerRequest] {
  override protected def refine[A](request: Request[A]): Future[Either[Result, ConsumerRequest[A]]] =
    authenticator.authenticate(request).flatMap {
      case Right(Some(consumer)) => Future.successful(Right(IdentifiedRequest(consumer, request)))
      case Right(None) =>
        val result = AppErrorTransformer.handleError(request, BrokenAuthError("Consumer not found in DB"))
        Future.successful(Left(result))
      case Left(error) =>
        val result = AppErrorTransformer.handleError(request, error)
        Future.successful(Left(result))
    }
}

object ConsumerAction {
  type ConsumerRequest[A] = IdentifiedRequest[Consumer, A]
}
