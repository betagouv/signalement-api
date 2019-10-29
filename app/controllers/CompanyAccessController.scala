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
