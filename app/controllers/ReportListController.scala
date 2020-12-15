package controllers

import java.util.UUID

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named, Singleton}
import models._
import orchestrators.ReportOrchestrator
import play.api.libs.json.{JsError, Json}
import play.api.{Configuration, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ReportStatus._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}
import utils.{DateUtils, SIRET}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportListController @Inject()(reportOrchestrator: ReportOrchestrator,
                                     reportRepository: ReportRepository,
                                     companyRepository: CompanyRepository,
                                     eventRepository: EventRepository,
                                     userRepository: UserRepository,
                                     mailerService: MailerService,
                                     s3Service: S3Service,
                                     @Named("reports-extract-actor") reportsExtractActor: ActorRef,
                                     val silhouette: Silhouette[AuthEnv],
                                     val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                     configuration: Configuration)
                                    (implicit val executionContext: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchCompany(user: User, siret: Option[String]): Future[Option[Company]] = {
    for {
      accesses <- companyRepository.fetchCompaniesWithLevel(user)
    } yield {
      siret.map(s => accesses.filter(_._1.siret == SIRET(s))).getOrElse(accesses).map(_._1).headOption
    }
  }

  def getReports(
                  offset: Option[Long],
                  limit: Option[Int],
                  departments: Option[String],
                  email: Option[String],
                  siret: Option[String],
                  companyName: Option[String],
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
      departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
      email,
      None,
      companyName,
      startDate,
      endDate,
      category,
      getStatusListForValueWithUserRole(status, request.identity.userRole),
      details,
      request.identity.userRole match {
        case UserRoles.Pro => Some(false)
        case _ => None
      },
      hasCompany,
      tags
    )

    for {
      company <- Some(request.identity)
                  .filter(_.userRole == UserRoles.Pro)
                  .map(u => fetchCompany(u, siret))
                  .getOrElse(Future(None))
      paginatedReports <- reportRepository.getReports(
                            offsetNormalized,
                            limitNormalized,
                            company.map(c => filter.copy(siret=Some(c.siret.value)))
                                   .getOrElse(filter)
      )
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
    } yield {
      Ok(Json.toJson(paginatedReports.copy(entities = paginatedReports.entities.map(r => ReportWithFiles(r, reportFilesMap.getOrElse(r.id, Nil))))))
    }
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async(parse.json) { implicit request =>
    implicit val reads = Json.reads[ReportsExtractActor.RawFilters]
    request.body.validate[ReportsExtractActor.RawFilters].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      filters =>
      for {
        restrictToCompany <- if (request.identity.userRole == UserRoles.Pro)
                                fetchCompany(request.identity, filters.siret)
                            else
                                Future(None)
      } yield {
        logger.debug(s"Requesting report for user ${request.identity.email}")
        reportsExtractActor ? ReportsExtractActor.ExtractRequest(
          request.identity,
          restrictToCompany,
          filters
        )
        Ok
      }
    )
  }
}

object ReportListObjects {
  case class ReportList(reportIds: List[UUID])
}
