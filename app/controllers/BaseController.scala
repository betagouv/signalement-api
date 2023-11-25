package controllers

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import controllers.error.AppErrorTransformer.handleError
import models._
import models.company.AccessLevel
import models.company.Company
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.mvc._
import repositories.company.CompanyRepositoryInterface
import utils.SIRET
import utils.auth.{Authenticator, CookieAuthenticator, JcaSigner, UserAction, UserRequest}
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

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

abstract class ApiKeyBaseController(override val controllerComponents: ControllerComponents)
    extends AbstractController(controllerComponents) {

  def silhouette: Silhouette[APIKeyEnv]
  type SecuredApiRequestWrapper[A] = SecuredRequest[APIKeyEnv, A]
  implicit val ec: ExecutionContext

  def SecuredAction: ActionBuilder[SecuredApiRequestWrapper, AnyContent] =
    silhouette.SecuredAction andThen new ErrorHandlerActionFunction[SecuredApiRequestWrapper](request =>
      Some(request.identity.id)
    )
}

abstract class BaseController(authenticator: Authenticator, override val controllerComponents: ControllerComponents)
    extends AbstractController(controllerComponents) {

//  type SecuredRequestWrapper[A]   = SecuredRequest[AuthEnv, A]
//  type UserAwareRequestWrapper[A] = UserAwareRequest[AuthEnv, A]

//  def silhouette: Silhouette[AuthEnv]

  implicit val ec: ExecutionContext

//  def SecuredAction: ActionBuilder[SecuredRequestWrapper, AnyContent] =
//    silhouette.SecuredAction andThen new ErrorHandlerActionFunction[SecuredRequestWrapper](request =>
//      Some(request.identity.id)
//    )
//
//  def SecuredAction(
//      authorization: Authorization[AuthEnv#I, AuthEnv#A]
//  ): ActionBuilder[SecuredRequestWrapper, AnyContent] =
//    silhouette.SecuredAction(authorization) andThen new ErrorHandlerActionFunction[SecuredRequestWrapper](request =>
//      Some(request.identity.id)
//    )
//
//  // We should always use our wrappers, to get our error handling
//  // We must NOT bind Action to UnsecuredAction as it was before
//  // It has not the same bahaviour : UnsecuredAction REJECTS a valid user connected when we just want to allow everyone
  override val Action: ActionBuilder[Request, AnyContent] =
    super.Action andThen new ErrorHandlerActionFunction[Request]()
//
//  def UnsecuredAction: ActionBuilder[Request, AnyContent] =
//    silhouette.UnsecuredAction andThen new ErrorHandlerActionFunction[Request]()
//
//  def UserAwareAction: ActionBuilder[UserAwareRequestWrapper, AnyContent] =
//    silhouette.UserAwareAction andThen
//      new ErrorHandlerActionFunction[UserAwareRequestWrapper](request => request.identity.map(_.id))
//
//  implicit def securedRequest2User[A](implicit req: SecuredRequest[AuthEnv, A]): User = req.identity
//
//  implicit def securedRequest2UserRoleOpt[A](implicit req: SecuredRequest[AuthEnv, A]): Option[UserRole] = Some(
//    req.identity.userRole
//  )
//
//  implicit def securedRequest2UserOpt[A](implicit req: SecuredRequest[AuthEnv, A]): Option[User] = Some(req.identity)
//
//  implicit def userAwareRequest2UserOpt[A](implicit req: UserAwareRequest[AuthEnv, A]): Option[User] = req.identity

  def SecuredAction: ActionBuilder[UserRequest, AnyContent] = new UserAction(new BodyParsers.Default(controllerComponents.parsers), authenticator) andThen new ErrorHandlerActionFunction[UserRequest]()

}

abstract class BaseCompanyController(override val controllerComponents: ControllerComponents)
    extends BaseController(controllerComponents) {
  def companyRepository: CompanyRepositoryInterface
  def companyVisibilityOrch: CompaniesVisibilityOrchestrator

  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: SecuredRequestWrapper[A])
      extends WrappedRequest[A](request) {
    def identity = request.identity
  }

  def withCompany(siret: String, authorizedLevels: Seq[AccessLevel]) =
    SecuredAction andThen new ActionRefiner[SecuredRequestWrapper, CompanyRequest] {
      def executionContext = ec
      def refine[A](request: SecuredRequestWrapper[A]) =
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
          .getOrElse(Left(NotFound))
    }
}
