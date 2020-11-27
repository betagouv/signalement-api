package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.{Company, UserRoles, Website, WebsiteCreate, WebsiteKind, WebsiteUpdate, WebsiteUpdateCompany}
import orchestrators.WebsiteOrchestrator
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.{CompanyRepository, WebsiteRepository}
import utils.silhouette.auth.{AuthEnv, WithRole}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebsiteController @Inject()(
  val websiteOrchestrator: WebsiteOrchestrator,
  val websiteRepository: WebsiteRepository,
  val companyRepository: CompanyRepository,
  val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def fetchWithCompanies() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      websites <- websiteRepository.list()
      websitesWithCompanies <- websiteOrchestrator.toJsonWithCompany(websites)
    } yield {
      Ok(Json.toJson(websitesWithCompanies))
    }
  }

  def update(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate =>
        for {
          websiteOption <- websiteRepository.find(uuid)
          updatedWebsiteOption <- websiteOption.map(website => {
            websiteRepository.update(websiteUpdate.mergeIn(website)).map(Some(_))
          }).getOrElse(Future(None))
          websiteWithCompanyJson <- websiteOrchestrator.toJsonWithCompany(updatedWebsiteOption)
        } yield {
          websiteWithCompanyJson.map(Ok(_)).getOrElse(NotFound)
        }
    )
  }

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdateCompany].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate =>
        for {
          websiteOption <- websiteRepository.find(uuid)
          company <- companyRepository.getOrCreate(websiteUpdate.companySiret, Company(
            siret = websiteUpdate.companySiret,
            name = websiteUpdate.companyName,
            address = websiteUpdate.companyAddress,
            postalCode = websiteUpdate.companyPostalCode,
            activityCode = websiteUpdate.companyActivityCode,
          ))
          updatedWebsite <- websiteOption.map(website =>
            websiteRepository.update(website.copy(companyId = company.id)).map(Some(_))
          ).getOrElse(Future(None))
          websiteWithCompany <- websiteOrchestrator.toJsonWithCompany(updatedWebsite)
        } yield {
          websiteWithCompany.map(Ok(_)).getOrElse(InternalServerError)
        }
    )
  }

  def create() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteCreate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteCreate =>
        for {
          company <- companyRepository.getOrCreate(websiteCreate.companySiret, Company(
            siret = websiteCreate.companySiret,
            name = websiteCreate.companyName,
            address = websiteCreate.companyAddress,
            postalCode = websiteCreate.companyPostalCode,
            activityCode = websiteCreate.companyActivityCode,
          ))
          website <- websiteRepository.create(Website(
            host = websiteCreate.host,
            kind = WebsiteKind.DEFAULT,
            companyId = company.id,
          ))
          websiteWithCompany <- websiteOrchestrator.toJsonWithCompany(website)
        } yield {
          websiteWithCompany.map(Ok(_)).getOrElse(InternalServerError)
        }
    )
  }

  def remove(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      _ <- websiteRepository.delete(uuid)
    } yield Ok
  }
}
