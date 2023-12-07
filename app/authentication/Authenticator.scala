package authentication

import controllers.error.AppError.AuthError
import play.api.mvc.Request

import scala.concurrent.Future

trait Authenticator[Identity] {
  def authenticate[T](request: Request[T]): Future[Either[AuthError, Option[Identity]]]
}
