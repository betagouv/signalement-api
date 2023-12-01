package utils.auth

import play.api.libs.json.Format
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import scala.concurrent.duration._

case class CookieInfos(
    id: String,
    userEmail: EmailAddress,
    lastUsedDateTime: OffsetDateTime,
    expirationDateTime: OffsetDateTime,
    idleTimeout: Option[FiniteDuration],
    cookieMaxAge: Option[FiniteDuration],
    fingerprint: Option[String]
)

object CookieInfos {
  implicit private val finiteDurationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {
    def reads(json: JsValue): JsResult[FiniteDuration] = LongReads.reads(json).map(_.seconds)

    def writes(o: FiniteDuration): JsValue = LongWrites.writes(o.toSeconds)
  }
  implicit val formatCookieInfos: OFormat[CookieInfos] = Json.format[CookieInfos]
}
