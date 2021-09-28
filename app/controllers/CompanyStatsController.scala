package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.CompanyStatsOrchestrator
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

  def getReportsCountEvolution(id: UUID, period: String) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    for {
      reportsCount <- _companyStats.getReportsCountByDate(id, Period.fromString(period))
      responsesCount <- _companyStats.getReportsResponsesCountByDate(id, Period.fromString(period))
    } yield {
      val indexedReportsCount = reportsCount.groupBy(_._1)
      val data = responsesCount.map(x =>
        ReportsCountEvolution(
          date = x._1,
          reports = indexedReportsCount.get(x._1).flatMap(_.headOption).map(_._2).getOrElse(0),
          responses = x._2
        )
      )
      Ok(Json.toJson(data))
    }
  }

  def getHosts(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getHosts(id).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportsTagsDistribution(id).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportsStatusDistribution(id).map(x => Ok(Json.toJson(x)))
  }

  def getReportResponseReviews(id: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportResponseReview(id).map(x => Ok(Json.toJson(x)))
  }

  def getReadAvgDelayInHours(companyId: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats
      .getReadAvgDelay(Some(companyId))
      .map(count => Ok(Json.obj("value" -> count.map(_.toHours))))
  }

  def getResponseAvgDelayInHours(companyId: UUID) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats
      .getResponseAvgDelay(Some(companyId))
      .map(count => Ok(Json.obj("value" -> count.map(_.toHours))))
  }
}
