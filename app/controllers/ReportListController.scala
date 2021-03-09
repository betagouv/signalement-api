package controllers

import java.util.UUID

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named, Singleton}
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories._
import utils.Constants.ReportStatus._
import utils.DateUtils
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportListController @Inject()(
  reportRepository: ReportRepository,
  companyRepository: CompanyRepository,
  companyDataRepository: CompanyDataRepository,
  reportListOrchestrator: CompaniesVisibilityOrchestrator,
  @Named("reports-extract-actor") reportsExtractActor: ActorRef,
  val silhouette: Silhouette[AuthEnv],
  val silhouetteAPIKey: Silhouette[APIKeyEnv])
  (implicit val executionContext: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def getReports(
    offset: Option[Long],
    limit: Option[Int],
    departments: Option[String],
    email: Option[String],
    websiteURL: Option[String],
    phone: Option[String],
    websiteRequired: Option[Boolean],
    phoneRequired: Option[Boolean],
    siretSirenList: List[String],
    companyName: Option[String],
    companyCountries: Option[String],
    start: Option[String],
    end: Option[String],
    category: Option[String],
    status: Option[String],
    details: Option[String],
    hasCompany: Option[Boolean],
    tags: List[String]
  ) = SecuredAction.async { implicit request =>

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseDate(end)

    val filter = ReportFilter(
      departments = departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
      email = email,
      websiteURL = websiteURL,
      phone = phone,
      websiteRequired = websiteRequired,
      phoneRequired = phoneRequired,
      siretSirenList = siretSirenList,
      companyName = companyName,
      companyCountries = companyCountries.map(d => d.split(",").toSeq).getOrElse(Seq()),
      start = startDate,
      end = endDate,
      category = category,
      statusList = getStatusListForValueWithUserRole(status, request.identity.userRole),
      details = details,
      employeeConsumer = request.identity.userRole match {
        case UserRoles.Pro => Some(false)
        case _ => None
      },
      hasCompany = hasCompany,
      tags = tags
    )

    for {
      sanitizedSirenSirets <- reportListOrchestrator.filterUnauthorizedSiretSirenList(siretSirenList, request.identity)
      paginatedReports <- reportRepository.getReports(
        offsetNormalized,
        limitNormalized,
        filter.copy(siretSirenList = sanitizedSirenSirets)
      )
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
    } yield {
      Ok(Json.toJson(paginatedReports.copy(entities = paginatedReports.entities.map(r => ReportWithFiles(r, reportFilesMap.getOrElse(r.id, Nil))))))
    }
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async(parse.json) { implicit request =>
    request.body.validate[ReportFilterBody].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      filters =>
        for {
          sanitizedSirenSirets <- reportListOrchestrator.filterUnauthorizedSiretSirenList(filters.siretSirenList, request.identity)
        } yield {
          logger.debug(s"Requesting report for user ${request.identity.email}")
          reportsExtractActor ? ReportsExtractActor.ExtractRequest(
            request.identity,
            filters.copy(siretSirenList = sanitizedSirenSirets)
          )
          Ok
        }
    )
  }
}

object ReportListObjects {

  case class ReportList(reportIds: List[UUID])

}
