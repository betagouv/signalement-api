import controllers.error.AppError.MalformedBody

import models.ReportResponseType
import models.website.WebsiteKind
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.mvc.QueryStringBindable
import play.api.mvc.Request
import cats.syntax.either._

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
        logger.error(s"Malformed request body : ${JsError.toJson(errors)}")
        MalformedBody
      }
      .liftTo[Future]
  }
}
