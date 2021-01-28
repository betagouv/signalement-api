package controllers

import actors.ReportedPhonesExtractActor
import actors.ReportedPhonesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models._
import play.api.Logger
import play.api.libs.json.Json
import repositories.{CompanyRepository, ReportRepository}
import utils.DateUtils
import utils.silhouette.auth.{AuthEnv, WithRole}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportedPhoneController @Inject()(
  val reportRepository: ReportRepository,
  val companyRepository: CompanyRepository,
  @Named("reported-phones-extract-actor") reportedPhonesExtractActor: ActorRef,
  val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchUnregisteredPhones(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    reportRepository.getPhoneReportsWithoutCompany(DateUtils.parseDate(start), DateUtils.parseDate(end))
      .map(reports => Ok(Json.toJson(
        reports
          .groupBy(_.phone)
          .collect { case (Some(phone), reports) if q.map(phone.contains(_)).getOrElse(true) => (phone, reports.length) }
          .map{ case(phone, count) => Json.obj("phone" -> phone, "count" -> count)}
      )))
  }

  def extractUnregisteredPhones(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    logger.debug(s"Requesting reportedPhones for user ${request.identity.email}")
    reportedPhonesExtractActor ? ReportedPhonesExtractActor.ExtractRequest(request.identity, RawFilters(q, start, end))
    Future(Ok)
  }
}
