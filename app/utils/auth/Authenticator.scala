package utils.auth

import models.User
import play.api.mvc.Request

import scala.concurrent.Future

trait Authenticator {
  def authenticate[B](request: Request[B]): Future[Option[User]]
}
