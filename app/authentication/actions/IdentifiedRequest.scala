package authentication.actions

import play.api.mvc.Request
import play.api.mvc.WrappedRequest

case class IdentifiedRequest[Identity, A](identity: Identity, request: Request[A]) extends WrappedRequest[A](request)
