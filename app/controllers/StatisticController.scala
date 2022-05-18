package controllers

import cats.data.NonEmptyList
import com.mohiva.play.silhouette.api.Silhouette
import models._
import models.report.ReportFilter
import models.report.ReportResponseType
import orchestrators.StatsOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.QueryStringMapper
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatisticController @Inject() (
    statsOrchestrator: StatsOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportsCount() = UserAwareAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString, request.identity.map(_.userRole).getOrElse(UserRole.Admin))
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters =>
          statsOrchestrator
            .getReportCount(filters)
            .map(count => Ok(Json.obj("value" -> count)))
      )
  }

  /** Nom de fonction adoubé par Saïd. En cas d'incompréhension, merci de le contacter directement
    */
  def getReportsCountCurve() = UserAwareAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString, request.identity.map(_.userRole).getOrElse(UserRole.Admin))
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters => {
          val mapper = new QueryStringMapper(request.queryString)
          val ticks = mapper.int("ticks").getOrElse(12)
          val tickDuration = mapper
            .string("tickDuration")
            .flatMap(CurveTickDuration.namesToValuesMap.get)
            .getOrElse(CurveTickDuration.Month)
          statsOrchestrator.getReportsCountCurve(filters, ticks, tickDuration).map(curve => Ok(Json.toJson(curve)))
        }
      )
  }

  def getDelayReportReadInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRole.Admin, UserRole.DGCCRF)
  ).async {
    statsOrchestrator
      .getReadAvgDelay(companyId)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getDelayReportResponseInHours(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator
      .getResponseAvgDelay(companyId: Option[UUID], request.identity.userRole)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseReviews(companyId: Option[UUID]) = SecuredAction.async {
    statsOrchestrator.getReportResponseReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator.getReportsTagsDistribution(companyId, request.identity.userRole).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator.getReportsStatusDistribution(companyId, request.identity.userRole).map(x => Ok(Json.toJson(x)))
  }

  def getProReportTransmittedStat(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.getProReportTransmittedStat(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def getProReportResponseStat(ticks: Option[Int], responseStatusQuery: Option[List[ReportResponseType]]) =
    SecuredAction.async(parse.empty) { _ =>
      val reportResponseStatus =
        NonEmptyList
          .fromList(responseStatusQuery.getOrElse(List.empty))
          .getOrElse(
            NonEmptyList.of(
              ReportResponseType.ACCEPTED,
              ReportResponseType.NOT_CONCERNED,
              ReportResponseType.REJECTED
            )
          )

      statsOrchestrator
        .getProReportResponseStat(
          ticks.getOrElse(12),
          reportResponseStatus
        )
        .map(x => Ok(Json.toJson(x)))

    }

  def dgccrfAccountsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfActiveAccountsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfActiveAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfSubscription(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfSubscription(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfControlsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfControlsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def countByDepartments() = SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { implicit request =>
    val mapper = new QueryStringMapper(request.queryString)
    val start = mapper.localDate("start")
    val end = mapper.localDate("end")
    statsOrchestrator.countByDepartments(start, end).map(res => Ok(Json.toJson(res)))
  }

}
