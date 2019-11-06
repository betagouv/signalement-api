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
                                val companyRepository: CompanyRepository,
                                val companyAccessRepository: CompanyAccessRepository,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {

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
