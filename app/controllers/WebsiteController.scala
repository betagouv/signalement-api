package controllers

import actors.WebsiteExtractActor
import org.apache.pekko.actor.typed
import authentication.Authenticator
import models.PaginatedResult.paginatedResultWrites
import models._
import models.company.CompanyCreation
import models.investigation.InvestigationStatus
import models.investigation.WebsiteInvestigationApi
import models.website._
import orchestrators.WebsitesOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import utils.URL

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class WebsiteController(
    val websitesOrchestrator: WebsitesOrchestrator,
    val companyRepository: CompanyRepositoryInterface,
    websitesExtractActor: typed.ActorRef[WebsiteExtractActor.WebsiteExtractCommand],
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds
  val logger: Logger                                  = Logger(this.getClass)

  def create() = Act.secured.admins.async(parse.json) { request =>
    request.body
      .validate[WebsiteCreation]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        websiteCreation =>
          websitesOrchestrator
            .create(URL(websiteCreation.host), websiteCreation.company.toCompany(), request.identity)
            .map(website => Ok(Json.toJson(website)))
      )
  }

  def fetchWithCompanies(
      maybeHost: Option[String],
      maybeIdentificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatus: Option[Seq[InvestigationStatus]],
      start: Option[OffsetDateTime],
      end: Option[OffsetDateTime],
      hasAssociation: Option[Boolean],
      isOpen: Option[Boolean],
      isMarketplace: Option[Boolean]
  ) =
    Act.secured.adminsAndReadonly.async { _ =>
      for {
        result <-
          websitesOrchestrator.getWebsiteCompanyCount(
            maybeHost.filter(_.nonEmpty),
            maybeIdentificationStatus.filter(_.nonEmpty),
            maybeOffset,
            maybeLimit,
            investigationStatus.filter(_.nonEmpty),
            start,
            end,
            hasAssociation,
            isOpen,
            isMarketplace
          )
        resultAsJson = Json.toJson(result)(paginatedResultWrites[WebsiteCompanyReportCount])
      } yield Ok(resultAsJson)
    }

  def fetchUnregisteredHost(
      host: Option[String],
      start: Option[String],
      end: Option[String],
      offset: Option[Long],
      limit: Option[Int]
  ) =
    Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { _ =>
      websitesOrchestrator
        .fetchUnregisteredHost(host, start, end, offset, limit)
        .map(websiteHostCount => Ok(Json.toJson(websiteHostCount)(paginatedResultWrites[WebsiteHostCount])))
    }

  def extractUnregisteredHost(q: Option[String], start: Option[String], end: Option[String]) =
    Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { implicit request =>
      logger.debug(s"Requesting websites for user ${request.identity.email}")
      websitesExtractActor ! WebsiteExtractActor.ExtractRequest(
        request.identity,
        WebsiteExtractActor.RawFilters(q.filter(_.nonEmpty), start, end)
      )
      Future.successful(Ok)
    }

  def searchByHost(url: String) = Act.public.standardLimit.async {
    websitesOrchestrator
      .searchByHost(url)
      .map(countries => Ok(Json.toJson(countries)))
  }

  def updateWebsite(
      websiteId: WebsiteId,
      identificationStatus: Option[IdentificationStatus],
      isMarketPlace: Option[Boolean]
  ) =
    Act.secured.admins.async { implicit request =>
      (identificationStatus, isMarketPlace) match {
        case (Some(identificationStatus), None) =>
          websitesOrchestrator
            .updateWebsiteIdentificationStatus(websiteId, identificationStatus, request.identity)
            .map(website => Ok(Json.toJson(website)))
        case (None, Some(isMarketPlace)) =>
          websitesOrchestrator
            .updateMarketplace(websiteId = websiteId, isMarketplace = isMarketPlace)
            .map(website => Ok(Json.toJson(website)))

        case _ => Future.successful(BadRequest)
      }
    }

  def updateCompany(websiteId: WebsiteId) =
    Act.secured.admins.async(parse.json) { implicit request =>
      request.body
        .validate[CompanyCreation]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          company =>
            websitesOrchestrator
              .updateCompany(websiteId, company, Some(request.identity))
              .map(websiteAndCompany => Ok(Json.toJson(websiteAndCompany)))
        )
    }

  def updateCompanyCountry(websiteId: WebsiteId, companyCountry: String) =
    Act.secured.admins.async { request =>
      websitesOrchestrator
        .updateCompanyCountry(websiteId, companyCountry, request.identity)
        .map(websiteAndCompany => Ok(Json.toJson(websiteAndCompany)))

    }

  def remove(websiteId: WebsiteId) = Act.secured.admins.async { _ =>
    websitesOrchestrator
      .delete(websiteId)
      .map(_ => Ok)
  }

  def updateInvestigation() = Act.secured.admins.async(parse.json) { implicit request =>
    for {
      websiteInvestigationApi <- request.parseBody[WebsiteInvestigationApi]()
      updated                 <- websitesOrchestrator.updateInvestigation(websiteInvestigationApi)
      _ = logger.debug(updated.toString)
    } yield Ok(Json.toJson(updated))
  }

  def listInvestigationStatus(): Action[AnyContent] =
    Act.secured.adminsAndReadonly { _ =>
      Ok(Json.toJson(websitesOrchestrator.listInvestigationStatus()))
    }

}
