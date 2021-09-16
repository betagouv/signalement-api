package controllers

import actors.WebsitesExtractActor
import actors.WebsitesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import cats.data.OptionT
import cats.syntax.option._
import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppError.WebsiteNotFound
import models.PaginatedResult.paginatedResultWrites
import models.WebsiteCompanyFormat._
import models._
import models.website.{WebsiteCompany, WebsiteCompanyReportCount}
import orchestrators.WebsitesOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories.CompanyRepository
import repositories.ReportRepository
import repositories.WebsiteRepository
import utils.DateUtils
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class WebsiteController @Inject() (
    val websitesOrchestrator: WebsitesOrchestrator,
    val websiteRepository: WebsiteRepository,
    val reportRepository: ReportRepository,
    val companyRepository: CompanyRepository,
    @Named("websites-extract-actor") websitesExtractActor: ActorRef,
    val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchWithCompanies(
      maybeHost: Option[String],
      maybeKinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ) =
    SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
      for {
        result <-
          websitesOrchestrator.getWebsiteCompanyCount(
            maybeHost.filter(_.nonEmpty),
            maybeKinds.filter(_.nonEmpty),
            maybeOffset,
            maybeLimit
          )
        resultAsJson = Json.toJson(result)(paginatedResultWrites[WebsiteCompanyReportCount])
      } yield Ok(resultAsJson)
    }

  def fetchUnregisteredHost(host: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
      reportRepository
        .getUnkonwnReportCountByHost(host, DateUtils.parseDate(start), DateUtils.parseDate(end))
        .map(_.collect { case (Some(host), count) =>
          Json.obj("host" -> host, "count" -> count)
        })
        .map(Json.toJson(_))
        .map(Ok(_))
    }

  def extractUnregisteredHost(q: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
      logger.debug(s"Requesting websites for user ${request.identity.email}")
      websitesExtractActor ? WebsitesExtractActor.ExtractRequest(
        request.identity,
        RawFilters(q.filter(_.nonEmpty), start, end)
      )
      Future(Ok)
    }

  def update(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[WebsiteUpdate]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        websiteUpdate =>
          (for {
            maybeWebsite <- websiteRepository.find(uuid)
            website <- maybeWebsite.liftTo[Future](WebsiteNotFound(uuid))
            _ <-
              if (websiteUpdate.kind.contains(WebsiteKind.DEFAULT)) unvalidateOtherWebsites(website)
              else Future.successful(())

            updatedWebsite <- websiteRepository.update(websiteUpdate.mergeIn(website))
            maybeCompany <- website.companyId match {
              case Some(id) => companyRepository.fetchCompany(id)
              case None     => Future.successful(None)
            }
          } yield WebsiteCompany()
//            (updatedWebsite, maybeCompany)).value.map {
//            case None         => NotFound
//            case Some(result) => Ok(Json.toJson(result))
//          }
      )
  }

  private[this] def unvalidateOtherWebsites(updatedWebsite: Website) =
    for {
      websitesWithSameHost <- websiteRepository
        .searchCompaniesByHost(updatedWebsite.host)
        .map(websites =>
          websites
            .map(_._1)
            .filter(_.id != updatedWebsite.id)
            .filter(_.kind == WebsiteKind.DEFAULT)
        )
      unvalidatedWebsites <-
        Future.sequence(
          websitesWithSameHost.map(website => websiteRepository.update(website.copy(kind = WebsiteKind.PENDING)))
        )
    } yield unvalidatedWebsites

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[WebsiteUpdateCompany]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        websiteUpdate => {
          val newCompanyFuture = companyRepository.getOrCreate(
            websiteUpdate.companySiret,
            Company(
              siret = websiteUpdate.companySiret,
              name = websiteUpdate.companyName,
              address = websiteUpdate.companyAddress,
              activityCode = websiteUpdate.companyActivityCode
            )
          )
          (for {
            website <- OptionT(websiteRepository.find(uuid))
            otherAssociatedCompaniesIds <-
              OptionT.liftF(websiteRepository.searchCompaniesByHost(website.host).map(_.map(_._2.siret)))
            newCompany <- OptionT.liftF(newCompanyFuture)
            result <- OptionT.liftF(if (otherAssociatedCompaniesIds.contains(websiteUpdate.companySiret)) {
              Future.successful(Conflict)
            } else {
              websiteRepository
                .update(website.copy(companyId = newCompany.id, kind = WebsiteKind.DEFAULT))
                .map(updated => Ok(Json.toJson((updated, newCompany))))
            })
          } yield result).value.map {
            case None         => NotFound
            case Some(result) => result
          }
        }
      )
  }

  def create() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[WebsiteCreate]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        websiteCreate =>
          for {
            company <- companyRepository.getOrCreate(
              websiteCreate.companySiret,
              Company(
                siret = websiteCreate.companySiret,
                name = websiteCreate.companyName,
                address = websiteCreate.companyAddress,
                activityCode = websiteCreate.companyActivityCode
              )
            )
            website <- websiteRepository.create(
              Website(
                host = websiteCreate.host,
                kind = WebsiteKind.DEFAULT,
                companyId = company.id
              )
            )
          } yield Ok(Json.toJson(website, company))
      )
  }

  def remove(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      _ <- websiteRepository.delete(uuid)
    } yield Ok
  }
}
