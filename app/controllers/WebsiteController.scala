package controllers

import actors.WebsitesExtractActor
import actors.WebsitesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppErrorTransformer.handleError
import models.PaginatedResult.paginatedResultWrites
import models._
import models.website._
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
          websitesOrchestrator
            .update(uuid, websiteUpdate)
            .map(x => Ok(Json.toJson(x)))
            .recover { case e => handleError(e) }
      )
  }

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[CompanyCreation]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        company =>
          websitesOrchestrator
            .updateCompany(uuid, company)
            .map(x => Ok(Json.toJson(x)))
            .recover { case e => handleError(e) }
      )
  }

  def create() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body
      .validate[WebsiteCreate]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        websiteCreate =>
          websitesOrchestrator
            .create(websiteCreate)
            .map(x => Ok(Json.toJson(x)))
            .recover { case e => handleError(e) }
      )
  }

  def remove(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    websiteRepository
      .delete(uuid)
      .map(_ => Ok)
      .recover { case e => handleError(e) }
  }
}
