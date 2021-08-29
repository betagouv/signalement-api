package controllers

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories._
import utils.Constants.ReportStatus._
import utils.DateUtils
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class ReportListController @Inject() (
    reportRepository: ReportRepository,
    reportOrchestrator: ReportOrchestrator,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    @Named("reports-extract-actor") reportsExtractActor: ActorRef,
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def getReports(
      offset: Option[Long],
      limit: Option[Int],
      departments: Option[String],
      email: Option[String],
      websiteURL: Option[String],
      phone: Option[String],
      websiteExists: Option[Boolean],
      phoneExists: Option[Boolean],
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
    val limitDefault = 25
    val limitMax = 250
    for {
      paginatedReports <- reportOrchestrator.getReportsForUser(
                            connectedUser = request.identity,
                            filter = ReportFilter(
                              departments = departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
                              email = email,
                              websiteURL = websiteURL,
                              phone = phone,
                              websiteExists = websiteExists,
                              phoneExists = phoneExists,
                              siretSirenList = siretSirenList.map(_.replaceAll("\\s", "")),
                              companyName = companyName,
                              companyCountries = companyCountries.map(d => d.split(",").toSeq).getOrElse(Seq()),
                              start = DateUtils.parseDate(start),
                              end = DateUtils.parseDate(end),
                              category = category,
                              statusList = getStatusListForValueWithUserRole(status, request.identity.userRole),
                              details = details,
                              employeeConsumer = request.identity.userRole match {
                                case UserRoles.Pro => Some(false)
                                case _             => None
                              },
                              hasCompany = hasCompany,
                              tags = tags
                            ),
                            offset = offset.map(Math.max(_, 0)).getOrElse(0),
                            limit.map(Math.max(_, 0)).map(Math.min(_, limitMax)).getOrElse(limitDefault)
                          )
    } yield Ok(Json.toJson(paginatedReports))
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportFilterBody]
      .map(filters => filters.copy(siretSirenList = filters.siretSirenList.map(_.replaceAll("\\s", ""))))
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        filters =>
          for {
            sanitizedSirenSirets <-
              companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(filters.siretSirenList, request.identity)
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
