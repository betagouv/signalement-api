package controllers

import java.util.UUID

import actors.WebsitesExtractActor
import actors.WebsitesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.WebsiteCompanyFormat._
import models._
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.{CompanyRepository, ReportRepository, WebsiteRepository}
import utils.DateUtils
import utils.silhouette.auth.{AuthEnv, WithRole}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebsiteController @Inject()(
  val websiteRepository: WebsiteRepository,
  val reportRepository: ReportRepository,
  val companyRepository: CompanyRepository,
  @Named("websites-extract-actor") websitesExtractActor: ActorRef,
  val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchWithCompanies() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      websites <- websiteRepository.list
      reports <- reportRepository.getWithWebsites()
      countByHostAndCompany = reports
        .groupBy(report => (report.websiteURL.flatMap(_.getHost), report.companyId))
        .collect { case ((Some(websiteURL), Some(companyId)), reports) => ((websiteURL, companyId), reports.length)}
      websitesWithCount = websites.map { case (website, company) => {
        val count = countByHostAndCompany.get(website.host, company.id).getOrElse(0)
        (website, company, count)
      }}
    } yield {
      Ok(Json.toJson(websitesWithCount))
    }
  }

  def fetchUnregisteredHost(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    reportRepository.getWebsiteReportsWithoutCompany(DateUtils.parseDate(start), DateUtils.parseDate(end))
      .map(reports => Ok(Json.toJson(
        reports
          .groupBy(_.websiteURL.flatMap(_.getHost))
          .collect { case (Some(host), reports) if q.map(host.contains(_)).getOrElse(true) => (host, reports.length) }
          .map { case (host, count) => Json.obj("host" -> host, "count" -> count) }
      )))
  }

  def extractUnregisteredHost(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    logger.debug(s"Requesting websites for user ${request.identity.email}")
    websitesExtractActor ? WebsitesExtractActor.ExtractRequest(request.identity, RawFilters(q, start, end))
    Future(Ok)
  }

  def update(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate => {
        (for {
          website <- OptionT(websiteRepository.find(uuid))
          _ <- OptionT.liftF(
            if (websiteUpdate.kind.contains(WebsiteKind.DEFAULT)) unvalidateOtherWebsites(website)
            else Future.successful(Unit)
          )
          updatedWebsite <- OptionT.liftF(websiteRepository.update(websiteUpdate.mergeIn(website)))
          company <- OptionT(companyRepository.fetchCompany(website.companyId))
        } yield (updatedWebsite, company)).value.map {
          case None => NotFound
          case Some(result) => Ok(Json.toJson(result))
        }
      }
    )
  }

  private[this] def unvalidateOtherWebsites(updatedWebsite: Website) = {
    for {
      websitesWithSameHost <- websiteRepository.searchCompaniesByHost(updatedWebsite.host).map(websites => websites
        .map(_._1)
        .filter(_.id != updatedWebsite.id)
        .filter(_.kind == WebsiteKind.DEFAULT)
      )
      unvalidatedWebsites <- Future.sequence(websitesWithSameHost.map(website => websiteRepository.update(website.copy(kind = WebsiteKind.PENDING))))
    } yield {
      unvalidatedWebsites
    }
  }

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[WebsiteUpdateCompany].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      websiteUpdate => {
        val newCompanyFuture = companyRepository.getOrCreate(websiteUpdate.companySiret, Company(
          siret = websiteUpdate.companySiret,
          name = websiteUpdate.companyName,
          address = websiteUpdate.companyAddress,
          postalCode = websiteUpdate.companyPostalCode,
          activityCode = websiteUpdate.companyActivityCode,
        ))
        (for {
          website <- OptionT(websiteRepository.find(uuid))
          otherAssociatedCompaniesIds <- OptionT.liftF(websiteRepository.searchCompaniesByHost(website.host).map(_.map(_._2.siret)))
          newCompany <- OptionT.liftF(newCompanyFuture)
          result <- OptionT.liftF(if (otherAssociatedCompaniesIds.contains(websiteUpdate.companySiret)) {
            Future.successful(Conflict)
          } else {
            websiteRepository
              .update(website.copy(companyId = newCompany.id, kind = WebsiteKind.DEFAULT))
              .map(updated => Ok(Json.toJson((updated, newCompany))))
          })
        } yield result).value.map {
          case None => NotFound
          case Some(result) => result
        }
      })
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
