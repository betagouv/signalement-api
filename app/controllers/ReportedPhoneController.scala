package controllers

import actors.ReportedPhonesExtractActor
import actors.ReportedPhonesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models._
import play.api.Logger
import play.api.libs.json.Json
import repositories.CompanyRepository
import repositories.ReportRepository
import utils.DateUtils
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class ReportedPhoneController @Inject() (
    val reportRepository: ReportRepository,
    val companyRepository: CompanyRepository,
    @Named("reported-phones-extract-actor") reportedPhonesExtractActor: ActorRef,
    val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchGrouped(q: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { _ =>
      reportRepository
        .getPhoneReports(DateUtils.parseDate(start), DateUtils.parseDate(end))
        .map(reports =>
          Ok(
            Json.toJson(
              reports
                .groupBy(report => (report.phone, report.companySiret, report.companyName, report.category))
                .collect {
                  case ((Some(phone), siretOpt, companyNameOpt, category), reports)
                      if q.map(phone.contains(_)).getOrElse(true) =>
                    ((phone, siretOpt, companyNameOpt, category), reports.length)
                }
                .map { case ((phone, siretOpt, companyNameOpt, category), count) =>
                  Json.obj(
                    "phone" -> phone,
                    "siret" -> siretOpt,
                    "companyName" -> companyNameOpt,
                    "category" -> category,
                    "count" -> count
                  )
                }
            )
          )
        )
    }

  def extractPhonesGroupBySIRET(q: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { implicit request =>
      logger.debug(s"Requesting reportedPhones for user ${request.identity.email}")
      reportedPhonesExtractActor ? ReportedPhonesExtractActor.ExtractRequest(
        request.identity,
        RawFilters(q, start, end)
      )
      Future(Ok)
    }
}
