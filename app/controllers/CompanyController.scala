package controllers

import javax.inject.{Inject, Singleton}
import java.util.UUID
import repositories._
import models._
import orchestrators.AccessesOrchestrator
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.{AuthEnv, WithRole}
import utils.{EmailAddress, SIRET}


@Singleton
class CompanyController @Inject()(
                                val userRepository: UserRepository,
                                val companyRepository: CompanyRepository,
                                val accessTokenRepository: AccessTokenRepository,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {
  def findCompany(q: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      company <- q match {
        case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => companyRepository.findByShortId(q)
        case q if q.matches("[0-9]{14}") => companyRepository.findBySiret(SIRET(q))
        case q => companyRepository.findByName(q)
      }
    } yield company.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
  }

  def companyDetails(siret: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      company   <- companyRepository.findBySiret(SIRET(siret))
      accesses  <- company.map(companyRepository.fetchUsersWithLevel(_)).getOrElse(Future(Nil))
      tokens    <- company.map(accessTokenRepository.fetchPendingTokens(_)).getOrElse(Future(Nil))
    } yield company.map(c => Ok(
      Json.obj(
        "company" -> Json.toJson(c),
        "accesses" -> accesses.map {case (u, l) => Json.obj(
          "firstName" -> u.firstName,
          "lastName" -> u.lastName,
          "email" -> u.email,
          "level" -> l,
        )},
        "invitations" -> tokens.map(t => Json.obj(
          "kind" -> t.kind,
          "level" -> t.companyLevel,
          "emailedTo" -> t.emailedTo
        ))
      )
    )).getOrElse(NotFound)
  }
}
