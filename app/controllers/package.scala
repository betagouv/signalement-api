import controllers.error.AppError.MalformedBody

import models.website.WebsiteKind
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.mvc.PathBindable
import play.api.mvc.QueryStringBindable
import play.api.mvc.Request
import cats.syntax.either._
import models.extractUUID
import models.report.ReportResponseType
import models.report.reportfile.ReportFileId
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

package object controllers {

  val logger: Logger = Logger(this.getClass)

  implicit val WebsiteKindQueryStringBindable: QueryStringBindable[WebsiteKind] =
    QueryStringBindable.bindableString
      .transform[WebsiteKind](
        kinds => WebsiteKind.fromValue(kinds),
        websiteKinds => websiteKinds.value
      )

  implicit val UUIDPathBindable =
    PathBindable.bindableString
      .transform[UUID](
        id => extractUUID(id),
        uuid => uuid.toString
      )

  implicit val ReportFileIdPathBindable =
    PathBindable.bindableString
      .transform[ReportFileId](
        id => ReportFileId(extractUUID(id)),
        reportFileId => reportFileId.value.toString
      )

  implicit val SIRETPathBindable =
    PathBindable.bindableString
      .transform[SIRET](
        siret => SIRET(siret),
        siret => siret.value
      )

  implicit val ReportResponseTypeQueryStringBindable: QueryStringBindable[ReportResponseType] =
    QueryStringBindable.bindableString
      .transform[ReportResponseType](
        reportResponseType => ReportResponseType.withName(reportResponseType),
        reportResponseType => reportResponseType.entryName
      )

  implicit class RequestOps[T <: JsValue](request: Request[T])(implicit ec: ExecutionContext) {
    def parseBody[B]()(implicit reads: Reads[B]) = request.body
      .validate[B]
      .asEither
      .leftMap { errors =>
        logger.error(s"Malformed request body  [error : ${JsError.toJson(errors)} , body ${request.body} ]")
        MalformedBody
      }
      .liftTo[Future]
  }
}
