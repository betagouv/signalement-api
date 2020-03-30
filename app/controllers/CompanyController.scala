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
                                val eventRepository: EventRepository,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {
  def findCompany(q: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      companies <- q match {
        case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => companyRepository.findByShortId(q)
        case q if q.matches("[0-9]{14}") => companyRepository.findBySiret(SIRET(q)).map(_.toList)
        case q => companyRepository.findByName(q)
      }
    } yield Ok(Json.toJson(companies))
  }

  def companyDetails(siret: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      company <- companyRepository.findBySiret(SIRET(siret))
    } yield company.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
  }

  def companiesToActivate() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      companies <- accessTokenRepository.companiesToActivate()
      eventsMap <- eventRepository.fetchContactEvents(companies.map(_.id))
    } yield Ok(
      Json.toJson(companies.map(c =>
        Json.obj(
          "company" -> Json.toJson(c),
          "lastNotice"  -> eventsMap.get(c.id).flatMap(_.headOption)
        )
      ))
    )
  }
}
