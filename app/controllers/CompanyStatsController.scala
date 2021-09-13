package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.CompanyStatsOrchestrator
import play.api.Logger
import play.api.libs.json._
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class CompanyStatsController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    val _companyStats: CompanyStatsOrchestrator
)(implicit ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportsCountEvolution(id: UUID, period: String) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats
      .getReportsCountByDate(id, CompanyReportsCountPeriod.fromString(period))
      .map(res => Ok(Json.toJson(res)))
  }

  def getHosts(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats.getHosts(id).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats.getReportsTagsDistribution(id).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats.getReportsStatusDistribution(id).map(x => Ok(Json.toJson(x)))
  }
}
