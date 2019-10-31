package controllers

import javax.inject.{Inject, Singleton}
import repositories._
import models._
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv


@Singleton
class CompanyAccessController @Inject()(
                                companyRepository: CompanyRepository,
                                companyAccessRepository: CompanyAccessRepository,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  // FIXME: Still experimental, will move
  import com.mohiva.play.silhouette.api.actions.SecuredRequest
  import utils.silhouette.auth.AuthEnv
  import play.api.mvc._

  type SecuredRequestWrapper[A] = SecuredRequest[AuthEnv, A]
  class CompanyRequest[A](val company: Company, val accessLevel: AccessLevel, request: SecuredRequestWrapper[A]) extends WrappedRequest[A](request) {
    def identity = request.identity
  }
  def withCompany[A](siret: String, authorizedLevels: Seq[AccessLevel])(implicit ec: ExecutionContext) = (SecuredAction andThen new ActionRefiner[SecuredRequestWrapper, CompanyRequest] {
    def executionContext = ec
    def refine[A](request: SecuredRequestWrapper[A]) = {
      for {
        company       <- companyRepository.findBySiret(siret)
        accessLevel   <- company.map(
                                    c => companyAccessRepository.getUserLevel(c.id, request.identity).map(Some(_)))
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

  def listAccesses(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      userAccesses <- companyAccessRepository.fetchUsersWithLevel(request.company)
    } yield Ok(Json.toJson(userAccesses.map{
      case (user, level) => Map(
          "firstName" -> user.firstName.getOrElse("—"),
          "lastName"  -> user.lastName.getOrElse("—"),
          "email"     -> user.email.getOrElse("—"),
          "level"     -> level.display
      )
    }))
  }

  def fetchTokenInfo(siret: String, token: String) = UnsecuredAction.async { implicit request =>
    for {
      company <- companyRepository.findBySiret(siret)
      token   <- company.map(companyAccessRepository.findToken(_, token))
                        .getOrElse(Future(None))
    } yield token.flatMap(t => company.map(c => 
      Ok(Json.toJson(TokenInfo(t.token, c.siret)))
    )).getOrElse(NotFound)
  }
}
