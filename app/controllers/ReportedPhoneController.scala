package controllers

import authentication.Authenticator
import authentication.actions.UserAction.WithRole
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import utils.DateUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ReportedPhoneController(
    val reportRepository: ReportRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface,
    asyncFileRepository: AsyncFileRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger                      = Logger(this.getClass)

  def fetchGrouped(q: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction.andThen(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { _ =>
      reportRepository
        .getPhoneReports(DateUtils.parseDate(start), DateUtils.parseDate(end))
        .map(reports =>
          Ok(
            Json.toJson(
              reports
                .groupBy(report => (report.phone, report.companySiret, report.companyName, report.category))
                .collect {
                  case ((Some(phone), siretOpt, companyNameOpt, category), reports) if q.forall(phone.contains(_)) =>
                    ((phone, siretOpt, companyNameOpt, category), reports.length)
                }
                .map { case ((phone, siretOpt, companyNameOpt, category), count) =>
                  Json.obj(
                    "phone"       -> phone,
                    "siret"       -> siretOpt,
                    "companyName" -> companyNameOpt,
                    "category"    -> category,
                    "count"       -> count
                  )
                }
            )
          )
        )
    }

  def extractPhonesGroupBySIRET(q: Option[String], start: Option[String], end: Option[String]) =
    SecuredAction.andThen(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { implicit request =>
      logger.debug(s"Requesting reportedPhones for user ${request.identity.email}")
      asyncFileRepository
        .create(AsyncFile.build(request.identity, kind = AsyncFileKind.ReportedPhones))
        .map(_ => Ok)
    }
}
