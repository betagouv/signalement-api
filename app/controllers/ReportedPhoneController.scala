package controllers

import actors.ReportedPhonesExtractActor
import actors.ReportedPhonesExtractActor.RawFilters
import actors.ReportedPhonesExtractActor.ReportedPhonesExtractCommand
import org.apache.pekko.actor.typed
import authentication.Authenticator
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import utils.DateUtils
import utils.PhoneNumberUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ReportedPhoneController(
    val reportRepository: ReportRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface,
    asyncFileRepository: AsyncFileRepositoryInterface,
    reportedPhonesExtractActor: typed.ActorRef[ReportedPhonesExtractCommand],
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds
  val logger: Logger                                  = Logger(this.getClass)

  def fetchGrouped(
      q: Option[String],
      start: Option[String],
      end: Option[String],
      offset: Option[Long],
      limit: Option[Int]
  ) =
    Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { _ =>
      reportRepository
        .getPhoneReports(
          readPhoneParam(q),
          DateUtils.parseDate(start),
          DateUtils.parseDate(end),
          offset,
          limit
        )
        .map(reports =>
          Ok(
            Json.toJson(
              reports
                .mapEntities { case ((phone, siretOpt, companyNameOpt), count) =>
                  Json.obj(
                    "phone"       -> phone,
                    "siret"       -> siretOpt,
                    "companyName" -> companyNameOpt,
                    "count"       -> count
                  )
                }
            )(PaginatedResult.paginatedResultWrites)
          )
        )
    }

  def extractPhonesGroupBySIRET(q: Option[String], start: Option[String], end: Option[String]) =
    Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { implicit request =>
      logger.debug(s"Requesting reportedPhones for user ${request.identity.email}")
      asyncFileRepository
        .create(AsyncFile.build(request.identity, kind = AsyncFileKind.ReportedPhones))
        .map { file =>
          reportedPhonesExtractActor ! ReportedPhonesExtractActor
            .ExtractRequest(
              file.id,
              request.identity,
              RawFilters(readPhoneParam(q), start, end)
            )
        }
        .map(_ => Ok)
    }

  private def readPhoneParam(q: Option[String]) =
    q.map(PhoneNumberUtils.sanitizeIncomingPhoneNumber)
}
