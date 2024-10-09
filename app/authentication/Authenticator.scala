package authentication

import controllers.error.AppError.BrokenAuthError
import play.api.mvc.Request

import scala.concurrent.Future

trait Authenticator[Identity] {
  def authenticate[T](request: Request[T]): Future[Either[BrokenAuthError, Option[Identity]]]
}
