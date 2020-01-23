package controllers

import javax.inject.{Inject, Singleton}
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import play.api.i18n.I18nSupport
import play.api.mvc.InjectedController
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import utils.silhouette.auth.AuthEnv
import utils.SIRET

import models._
import repositories._

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
  def companyAccessRepository: CompanyAccessRepository

  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: SecuredRequestWrapper[A]) extends WrappedRequest[A](request) {
    def identity = request.identity
  }
  def withCompany[A](siret: String, authorizedLevels: Seq[AccessLevel])(implicit ec: ExecutionContext) = (SecuredAction andThen new ActionRefiner[SecuredRequestWrapper, CompanyRequest] {
    def executionContext = ec
    def refine[A](request: SecuredRequestWrapper[A]) = {
      for {
        company       <- companyRepository.findBySiret(SIRET(siret))
        accessLevel   <- company.map(
                                    c => companyRepository.getUserLevel(c.id, request.identity).map(Some(_)))
                                .getOrElse(Future(None))
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
