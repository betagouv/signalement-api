package utils.auth

import controllers.error.AppError.AuthError
import models.Consumer
import play.api.mvc.ActionBuilder
import play.api.mvc.ActionTransformer
import play.api.mvc.AnyContent
import play.api.mvc.BodyParsers
import play.api.mvc.Request
import utils.auth.ConsumerAction.ConsumerRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ConsumerAction(val parser: BodyParsers.Default, authenticator: Authenticator[Consumer])(implicit
    val executionContext: ExecutionContext
) extends ActionBuilder[ConsumerRequest, AnyContent]
    with ActionTransformer[Request, ConsumerRequest] {
  override protected def transform[A](request: Request[A]): Future[ConsumerRequest[A]] =
    authenticator.authenticate(request).flatMap {
      case Some(consumer) => Future.successful(IdentifiedRequest(consumer, request))
      case None           => Future.failed(AuthError("Consumer not found in DB"))
    }
}

object ConsumerAction {
  type ConsumerRequest[A] = IdentifiedRequest[Consumer, A]
}
