package authentication

import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._
import utils.EmailAddress

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class CookieInfos(
    id: String,
    userEmail: EmailAddress,
    impersonator: Option[EmailAddress],
    lastUsedDateTime: OffsetDateTime,
    expirationDateTime: OffsetDateTime,
    idleTimeout: Option[FiniteDuration],
    cookieMaxAge: Option[FiniteDuration]
)

object CookieInfos {
  implicit private val finiteDurationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {
    def reads(json: JsValue): JsResult[FiniteDuration] = LongReads.reads(json).map(_.seconds)

    def writes(o: FiniteDuration): JsValue = LongWrites.writes(o.toSeconds)
  }
  implicit val formatCookieInfos: OFormat[CookieInfos] = Json.format[CookieInfos]
}
