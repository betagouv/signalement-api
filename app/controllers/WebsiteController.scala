package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.{Company, UserRoles, Website, WebsiteCreate, WebsiteKind, WebsiteUpdate, WebsiteUpdateCompany}
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.{CompanyRepository, WebsiteRepository}
import utils.silhouette.auth.{AuthEnv, WithRole}
import cats.data.OptionT
import scala.concurrent.{ExecutionContext, Future}
import models.WebsiteCompanyFormat._
import cats.implicits._

@Singleton
class WebsiteController @Inject()(
  val websiteRepository: WebsiteRepository,
  val companyRepository: CompanyRepository,
  val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def fetchWithCompanies() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      websites <- websiteRepository.list()
    } yield {
      Ok(Json.toJson(websites))
    }
  }

  def update(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate => {
        (for {
          website <- OptionT(websiteRepository.find(uuid))
          updatedWebsite <- OptionT.liftF(websiteRepository.update(websiteUpdate.mergeIn(website)))
          company <- OptionT(companyRepository.fetchCompany(website.companyId))
        } yield (updatedWebsite, company)).value.map {
          case None => NotFound
          case Some(result) => Ok(Json.toJson(result))
        }
      }
    )
  }

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdateCompany].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate =>
        (for {
          website <- OptionT(websiteRepository.find(uuid))
          company <- OptionT.liftF(companyRepository.getOrCreate(websiteUpdate.companySiret, Company(
            siret = websiteUpdate.companySiret,
            name = websiteUpdate.companyName,
            address = websiteUpdate.companyAddress,
            postalCode = websiteUpdate.companyPostalCode,
            activityCode = websiteUpdate.companyActivityCode,
          )))
          websiteUpdate <- OptionT.liftF(websiteRepository.update(website.copy(companyId = company.id)))
        } yield (websiteUpdate, company)).value.map {
          case None => NotFound
          case Some(result) => Ok(Json.toJson(result))
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
        } yield {
          Ok(Json.toJson(website, company))
        }
    )
  }

  def remove(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      _ <- websiteRepository.delete(uuid)
    } yield Ok
  }
}
