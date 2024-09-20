package authentication.actions

import models.User
import play.api.mvc.Results.Forbidden
import play.api.mvc.ActionFilter
import play.api.mvc.Result

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ImpersonationAction {
  type UserRequest[A] = IdentifiedRequest[User, A]

  def ForbidImpersonation(implicit ec: ExecutionContext): ActionFilter[UserRequest] = new ActionFilter[UserRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] =
      Future.successful(
        request.identity.impersonator match {
          case Some(_) => Some(Forbidden)
          case None    => None
        }
      )
  }
}
