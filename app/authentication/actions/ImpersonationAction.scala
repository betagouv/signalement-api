package authentication.actions

import authentication.actions.MaybeUserAction.MaybeUserRequest
import controllers.CompanyRequest
import models.User
import play.api.mvc.Results.Forbidden
import play.api.mvc.ActionFilter
import play.api.mvc.Result
import utils.EmailAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ImpersonationAction {
  type UserRequest[A] = IdentifiedRequest[User, A]

  def forbidImpersonationFilter(implicit ec: ExecutionContext): ActionFilter[UserRequest] =
    new ActionFilter[UserRequest] {
      override protected def executionContext: ExecutionContext = ec
      override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] =
        handleImpersonator(request.identity.impersonator)

    }

  def forbidImpersonationOnMaybeUserFilter(implicit ec: ExecutionContext): ActionFilter[MaybeUserRequest] =
    new ActionFilter[MaybeUserRequest] {
      override protected def executionContext: ExecutionContext = ec
      override protected def filter[A](request: MaybeUserRequest[A]): Future[Option[Result]] =
        handleImpersonator(request.identity.flatMap(_.impersonator))
    }

  def forbidImpersonationOnCompanyRequestFilter(implicit ec: ExecutionContext): ActionFilter[CompanyRequest] =
    new ActionFilter[CompanyRequest] {
      override protected def executionContext: ExecutionContext = ec
      override protected def filter[A](request: CompanyRequest[A]): Future[Option[Result]] =
        handleImpersonator(request.identity.impersonator)
    }

  private def handleImpersonator(maybeImpersonator: Option[EmailAddress]) =
    Future.successful(
      maybeImpersonator match {
        case Some(_) => Some(Forbidden)
        case None    => None
      }
    )

}
