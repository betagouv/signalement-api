package utils.auth

import models.User
import play.api.mvc.{Request, WrappedRequest}

case class UserRequest[A](user: User, request: Request[A]) extends WrappedRequest[A](request)
