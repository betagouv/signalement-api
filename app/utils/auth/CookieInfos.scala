package utils.auth

import play.api.libs.json.{Json, OFormat}
import utils.EmailAddress

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

case class CookieInfos (
                         id: String,
                         userEmail: EmailAddress,
                         lastUsedDateTime: OffsetDateTime,
                         expirationDateTime: OffsetDateTime,
                         idleTimeout: Option[FiniteDuration],
                         cookieMaxAge: Option[FiniteDuration],
                         fingerprint: Option[String]
                       )

object CookieInfos {
  implicit val formatCookieInfos: OFormat[CookieInfos] = Json.format[CookieInfos]
}