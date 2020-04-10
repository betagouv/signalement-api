package controllers

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import models._
import play.api.mvc.{InjectedController, _}
import repositories._
import utils.SIRET
import utils.silhouette.auth.AuthEnv

import scala.concurrent.{ExecutionContext, Future}

trait BaseController extends InjectedController {

  def silhouette: Silhouette[AuthEnv]

  def SecuredAction = silhouette.SecuredAction

  def UnsecuredAction = silhouette.UnsecuredAction

  def UserAwareAction = silhouette.UserAwareAction

  implicit def securedRequest2User[A](implicit req: SecuredRequest[AuthEnv, A]) = req.identity

  implicit def securedRequest2UserRoleOpt[A](implicit req: SecuredRequest[AuthEnv, A]) = Some(req.identity.userRole)

  implicit def securedRequest2UserOpt[A](implicit req: SecuredRequest[AuthEnv, A]) = Some(req.identity)

  implicit def userAwareRequest2UserOpt[A](implicit req: UserAwareRequest[AuthEnv, A]) = req.identity
}

trait BaseCompanyController extends BaseController {
  type SecuredRequestWrapper[A] = SecuredRequest[AuthEnv, A]
  def companyRepository: CompanyRepository
  def accessTokenRepository: AccessTokenRepository

  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: SecuredRequestWrapper[A]) extends WrappedRequest[A](request) {
    def identity = request.identity
  }
  def withCompany[A](siret: String, authorizedLevels: Seq[AccessLevel])(implicit ec: ExecutionContext) = (SecuredAction andThen new ActionRefiner[SecuredRequestWrapper, CompanyRequest] {
    def executionContext = ec
    def refine[A](request: SecuredRequestWrapper[A]) = {
      for {
        company       <- companyRepository.findBySiret(SIRET(siret))
        accessLevel   <- if (request.identity.userRole == UserRoles.Admin) Future(Some(AccessLevel.ADMIN))
                         else company.map(c =>
                                companyRepository.getUserLevel(c.id, request.identity).map(Some(_))
                              ).getOrElse(Future(None))
      } yield {
        company
          .flatMap(c => accessLevel.map((c, _)))
          .filter{case (_, l) => authorizedLevels.contains(l)}
          .map{case (c, l) => Right(new CompanyRequest[A](c, l, request))}
          .getOrElse(Left(NotFound))
      }
    }
  })
}
