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
import orchestrators.CompanyOrchestrator
import play.api.mvc.ActionBuilder
import play.api.mvc._
import utils.SIRET
import ConsumerAction.ConsumerRequest
import MaybeUserAction.MaybeUserRequest
import UserAction.UserRequest
import UserAction.WithAuthProvider
import UserAction.WithRole
import authentication.actions.ImpersonationAction.forbidImpersonationFilter
import authentication.actions.ImpersonationAction.forbidImpersonationOnMaybeUserFilter
import com.digitaltangible.playguard.IpRateLimitFilter
import com.digitaltangible.ratelimit.RateLimiter
import models.AuthProvider.ProConnect
import models.AuthProvider.SignalConso
import models.UserRole.Admins
import models.UserRole.AdminsAndAgents
import models.UserRole.AdminsAndReadOnly
import models.UserRole.AdminsAndReadOnlyAndAgents
import models.UserRole.AdminsAndReadOnlyAndCCRF
import models.UserRole.Professionnel
import models.UserRole.SuperAdmin

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

  private val securedByApiKeyAction = new ConsumerAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[ConsumerRequest]()

  trait ActDslApiKey {
    val securedbyApiKey = securedByApiKeyAction

  }
  val Act = new ActDslApiKey {}
}

abstract class BaseController(
    authenticator: Authenticator[User],
    override val controllerComponents: ControllerComponents,
    enableRateLimit: Boolean = true
) extends AbstractController(controllerComponents) {
  implicit val ec: ExecutionContext

  private def ipRateLimitFilter[F[_] <: Request[_]](
      size: Long,
      // refill rate, in number of token per second
      rate: Double
  ): IpRateLimitFilter[F] =
    new IpRateLimitFilter[F](new RateLimiter(size, rate, "Rate limit by IP address")) {
      override def rejectResponse[A](implicit request: F[A]): Future[Result] =
        Future.successful(TooManyRequests(s"""Rate limit exceeded"""))
    }

  // We should always use our wrappers, to get our error handling
  // We must NOT bind Action to UnsecuredAction as it was before
  // It has not the same behaviour : UnsecuredAction REJECTS a valid user connected when we just want to allow everyone
  override val Action: ActionBuilder[Request, AnyContent] =
    super.Action andThen new ErrorHandlerActionFunction[Request]()

  protected val securedAction: ActionBuilder[UserRequest, AnyContent] = new UserAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[UserRequest]()

  private val userAwareAction: ActionBuilder[MaybeUserRequest, AnyContent] = new MaybeUserAction(
    new BodyParsers.Default(controllerComponents.parsers),
    authenticator
  ) andThen new ErrorHandlerActionFunction[MaybeUserRequest]()

  case class AskImpersonationDsl(private val actionBuilder: ActionBuilder[UserRequest, AnyContent]) {
    val allowImpersonation  = actionBuilder
    val forbidImpersonation = actionBuilder.andThen(forbidImpersonationFilter)
  }

  case class AskImpersonationDslOnMaybeUser(
      private val actionBuilder: ActionBuilder[MaybeUserRequest, AnyContent]
  ) {
    val allowImpersonation  = actionBuilder
    val forbidImpersonation = actionBuilder.andThen(forbidImpersonationOnMaybeUserFilter)
  }

  trait ActDsl {
    object public {
      val generousLimit: ActionBuilder[Request, AnyContent] =
        // 72 = 12 pièces jointes * 3 pour la marge d'erreur * 2 car POST + GET systématique à chaque PJ
        if (enableRateLimit) Action andThen ipRateLimitFilter[Request](72, 1f / 5) else Action
      val standardLimit: ActionBuilder[Request, AnyContent] =
        if (enableRateLimit) Action andThen ipRateLimitFilter[Request](16, 1f / 5) else Action
      val tightLimit: ActionBuilder[Request, AnyContent] =
        // for potentially expensive endpoints (call to external apis)
        if (enableRateLimit) Action andThen ipRateLimitFilter[Request](3, 1f / 5) else Action

    }
    object secured {
      val all = AskImpersonationDsl(securedAction)
      val adminsAndReadonlyAndAgents = AskImpersonationDsl(
        securedAction.andThen(WithRole(AdminsAndReadOnlyAndAgents))
      )
      val adminsAndReadonlyAndDgccrf = AskImpersonationDsl(
        securedAction.andThen(WithRole(AdminsAndReadOnlyAndCCRF))
      )
      val pros            = AskImpersonationDsl(securedAction.andThen(WithRole(Professionnel)))
      val adminsAndAgents = AskImpersonationDsl(securedAction.andThen(WithRole(AdminsAndAgents)))
      // these roles cannot be impersonated
      val admins            = securedAction.andThen(WithRole(Admins))
      val superAdmins       = securedAction.andThen(WithRole(SuperAdmin))
      val adminsAndReadonly = securedAction.andThen(WithRole(AdminsAndReadOnly))

      object restrictByProvider {
        val signalConso = AskImpersonationDsl(securedAction.andThen(WithAuthProvider(SignalConso)))
        val proConnect  = AskImpersonationDsl(securedAction.andThen(WithAuthProvider(ProConnect)))
      }

    }
    val userAware = AskImpersonationDslOnMaybeUser(userAwareAction)
  }
  val Act = new ActDsl {}

}

abstract class BaseCompanyController(
    authenticator: Authenticator[User],
    override val controllerComponents: ControllerComponents
) extends BaseController(authenticator, controllerComponents) {
  def companyOrchestrator: CompanyOrchestrator
  def companyVisibilityOrchestrator: CompaniesVisibilityOrchestrator

  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: UserRequest[A])
      extends WrappedRequest[A](request) {
    def identity = request.identity
  }

  private def companyAccessActionRefiner(idOrSiret: Either[SIRET, UUID], adminLevelOnly: Boolean) =
    new ActionRefiner[UserRequest, CompanyRequest] {
      val authorizedLevels =
        if (adminLevelOnly)
          Seq(AccessLevel.ADMIN)
        else
          Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)

      def executionContext = ec

      def refine[A](request: UserRequest[A]) =
        for {
          company <- companyOrchestrator.getByIdOrSiret(idOrSiret)
          accessLevel <-
            request.identity.userRole match {
              case UserRole.SuperAdmin | UserRole.Admin | UserRole.ReadOnlyAdmin | UserRole.DGCCRF =>
                Future.successful(Some(AccessLevel.ADMIN))
              case UserRole.DGAL | UserRole.Professionnel =>
                company
                  .map(c =>
                    companyVisibilityOrchestrator
                      .fetchVisibleCompanies(request.identity)
                      .map(_.find(_.company.id == c.id).map(_.level))
                  )
                  .getOrElse(Future.successful(None))
            }
        } yield company
          .flatMap(c => accessLevel.map((c, _)))
          .filter { case (_, l) => authorizedLevels.contains(l) }
          .map { case (c, l) => Right(new CompanyRequest[A](c, l, request)) }
          .getOrElse(Left(Forbidden))
    }

  trait ActDslWithCompanyAccess extends ActDsl {
    def securedWithCompanyAccessBySiret(siret: String, adminLevelOnly: Boolean = false) =
      securedAction.andThen(companyAccessActionRefiner(Left(SIRET.fromUnsafe(siret)), adminLevelOnly))
    def securedWithCompanyAccessById(id: UUID, adminLevelOnly: Boolean = false) =
      securedAction.andThen(companyAccessActionRefiner(Right(id), adminLevelOnly))
  }
  override val Act = new ActDslWithCompanyAccess {}

}
