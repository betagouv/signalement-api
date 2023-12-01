package utils.auth

import play.api.mvc.Request

import scala.concurrent.Future

trait Authenticator[Identity] {
  def authenticate[T](request: Request[T]): Future[Option[Identity]]
}
