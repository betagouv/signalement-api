package controllers

import authentication.actions.ConsumerAction
import authentication.actions.MaybeUserAction
import authentication.actions.UserAction
import authentication.Authenticator
import controllers.error.AppErrorTransformer.handleError
import models._
import models.company.AccessLevel
import models.company.Company
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.mvc._
import repositories.company.CompanyRepositoryInterface
import utils.SIRET
import ConsumerAction.ConsumerRequest
import MaybeUserAction.MaybeUserRequest
import UserAction.UserRequest
import com.digitaltangible.playguard.IpRateLimitFilter
import com.digitaltangible.ratelimit.RateLimiter

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ErrorHandlerActionFunction[R[_] <: play.api.mvc.Request[_]](
    getIdentity: R[_] => Option[UUID] = (_: R[_]) => None
)(implicit
    ec: ExecutionContext
) extends ActionFunction[R, R] {

  def invokeBlock[A](
      request: R[A],
      block: R[A] => Future[Result]
  ): Future[Result] =
    // An error may happen either in the result of the future,
    // or when building the future itself
    Try {
      block(request).recover { case err =>
        handleError(request, err, getIdentity(request))
      }
    } match {
      case Success(res) => res
      case Failure(err) =>
        Future.successful(handleError(request, err, getIdentity(request)))
    }

  override protected def executionContext: ExecutionContext = ec
}

abstract class ApiKeyBaseController(
    authenticator: Authenticator[Consumer],
    override val controllerComponents: ControllerComponents
) extends AbstractController(controllerComponents) {

  implicit val ec: ExecutionContext

  def SecuredAction = new ConsumerAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[ConsumerRequest]()
}

abstract class BaseController(
    authenticator: Authenticator[User],
    override val controllerComponents: ControllerComponents,
    enableRateLimit: Boolean = true
) extends AbstractController(controllerComponents) {
  implicit val ec: ExecutionContext

  private def ipRateLimitFilter[F[_] <: Request[_]](size: Long, rate: Double): IpRateLimitFilter[F] =
    new IpRateLimitFilter[F](new RateLimiter(size, rate, "Rate limit by IP address")) {
      override def rejectResponse[A](implicit request: F[A]): Future[Result] =
        Future.successful(TooManyRequests(s"""Rate limit exceeded"""))
    }

  // We should always use our wrappers, to get our error handling
  // We must NOT bind Action to UnsecuredAction as it was before
  // It has not the same bahaviour : UnsecuredAction REJECTS a valid user connected when we just want to allow everyone
  override val Action: ActionBuilder[Request, AnyContent] =
    super.Action andThen new ErrorHandlerActionFunction[Request]()

  def SecuredAction: ActionBuilder[UserRequest, AnyContent] = new UserAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[UserRequest]()

  def UserAwareAction: ActionBuilder[MaybeUserRequest, AnyContent] = new MaybeUserAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[MaybeUserRequest]()

  def IpRateLimitedAction1: ActionBuilder[Request, AnyContent] =
    if (enableRateLimit) Action andThen ipRateLimitFilter[Request](36, 1f / 5) else Action
  def IpRateLimitedAction2: ActionBuilder[Request, AnyContent] =
    if (enableRateLimit) Action andThen ipRateLimitFilter[Request](9, 1f / 5) else Action
  def IpRateLimitedAction3: ActionBuilder[Request, AnyContent] =
    if (enableRateLimit) Action andThen ipRateLimitFilter[Request](3, 1f / 5) else Action
}

abstract class BaseCompanyController(
    authenticator: Authenticator[User],
    override val controllerComponents: ControllerComponents
) extends BaseController(authenticator, controllerComponents) {
  def companyRepository: CompanyRepositoryInterface
  def companyVisibilityOrch: CompaniesVisibilityOrchestrator

  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: UserRequest[A])
      extends WrappedRequest[A](request) {
    def identity = request.identity
  }

  def withCompanyAccess(siret: String, adminLevelOnly: Boolean = false) =
    SecuredAction andThen new ActionRefiner[UserRequest, CompanyRequest] {
      val authorizedLevels =
        if (adminLevelOnly)
          Seq(AccessLevel.ADMIN)
        else
          Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
      def executionContext = ec
      def refine[A](request: UserRequest[A]) =
        for {
          company <- companyRepository.findBySiret(SIRET.fromUnsafe(siret))
          accessLevel <-
            if (Seq(UserRole.Admin, UserRole.DGCCRF).contains(request.identity.userRole))
              Future(Some(AccessLevel.ADMIN))
            else
              company
                .map(c =>
                  companyVisibilityOrch
                    .fetchVisibleCompanies(request.identity)
                    .map(_.find(_.company.id == c.id).map(_.level))
                )
                .getOrElse(Future(None))
        } yield company
          .flatMap(c => accessLevel.map((c, _)))
          .filter { case (_, l) => authorizedLevels.contains(l) }
          .map { case (c, l) => Right(new CompanyRequest[A](c, l, request)) }
          .getOrElse(Left(Forbidden))
    }
}
