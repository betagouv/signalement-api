package controllers

import com.mohiva.play.silhouette.api.Silhouette
import io.scalaland.chimney.dsl.TransformerOps
import models.PaginatedResult.paginatedResultWrites
import models._
import models.investigation.WebsiteInvestigationApi
import models.investigation.WebsiteInvestigationCompanyReportCount
import models.website._
import orchestrators.WebsiteInvestigationOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WebsiteInvestigationController(
    val orchestrator: WebsiteInvestigationOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def list(
      maybeHost: Option[String],
      maybeKinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Action[AnyContent] =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { _ =>
      for {
        result <-
          orchestrator.list(
            maybeHost.filter(_.nonEmpty),
            maybeKinds.filter(_.nonEmpty),
            maybeOffset,
            maybeLimit
          )
        resultAsJson = Json.toJson(result)(paginatedResultWrites[WebsiteInvestigationCompanyReportCount])
      } yield Ok(resultAsJson)
    }

  def createOrUpdate() = SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async(parse.json) {
    implicit request =>
      for {
        websiteInvestigationApi <- request.parseBody[WebsiteInvestigationApi]()
        updated <- orchestrator.createOrUpdate(websiteInvestigationApi)
        x = updated.into[WebsiteInvestigationApi].transform
      } yield Ok(Json.toJson(x))
  }

  def listDepartmentDivision(): Action[AnyContent] =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)) { _ =>
      Ok(Json.toJson(orchestrator.listDepartmentDivision()))
    }

  def listInvestigationStatus(): Action[AnyContent] =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)) { _ =>
      Ok(Json.toJson(orchestrator.listInvestigationStatus()))
    }

  def listPractice(): Action[AnyContent] =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)) { _ =>
      Ok(Json.toJson(orchestrator.listPractice()))
    }

}
