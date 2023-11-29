package utils.auth

import controllers.error.AppError.AuthError
import models.User
import play.api.libs.json.Json
import play.api.mvc.Cookie
import play.api.mvc.DiscardingCookie
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import repositories.user.UserRepositoryInterface
import utils.EmailAddress
import utils.auth.CookieAuthenticator.CookieAuthenticatorSettings

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

class CookieAuthenticator(
    signer: JcaSigner,
    crypter: JcaCrypter,
    fingerprintGenerator: FingerprintGenerator,
    settings: CookieAuthenticatorSettings,
    userRepository: UserRepositoryInterface
) extends Authenticator[User] {

  private def unserialize(str: String): Either[AuthError, CookieInfos] =
    for {
      data          <- signer.extract(str)
      decryptedData <- crypter.decrypt(data)
      cookiesInfos <- Json
        .parse(decryptedData)
        .validate[CookieInfos]
        .asEither
        .left
        .map(_ => AuthError("Error while extracting data from cookie"))
    } yield cookiesInfos

  def extract[B](request: Request[B]): Either[AuthError, CookieInfos] = {
    val maybeFingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate(request)) else None
    val test = request.cookies.get(settings.cookieName) match {
      case Some(cookie) => unserialize(cookie.value)
      case None         => Left(AuthError("Cookie not found"))
    }

    test match {
      case Right(a) if maybeFingerprint.isDefined && a.fingerprint != maybeFingerprint =>
        Left(AuthError("Fingerprint does not match"))
      case v => v
    }
  }

  def authenticate[B](request: Request[B]): Future[Option[User]] =
    extract(request) match {
      case Right(cookieInfos) => userRepository.findByEmail(cookieInfos.userEmail.value)
      case Left(authError)    => Future.failed(authError)
    }

  private def serialize(cookieInfos: CookieInfos): Either[AuthError, String] = for {
    crypted <- crypter.encrypt(Json.toJson(cookieInfos).toString())
  } yield signer.sign(crypted)

  private def create(userEmail: EmailAddress)(implicit request: RequestHeader): CookieInfos = {
    val now = OffsetDateTime.now()
    CookieInfos(
      id = UUID.randomUUID().toString,
      userEmail = userEmail,
      lastUsedDateTime = now,
      expirationDateTime = now.plus(settings.authenticatorExpiry.toMillis, ChronoUnit.MILLIS),
      idleTimeout = settings.authenticatorIdleTimeout,
      cookieMaxAge = settings.cookieMaxAge,
      fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate(request)) else None
    )
  }

  def init(userEmail: EmailAddress)(implicit request: RequestHeader): Either[AuthError, Cookie] = {
    val cookieInfos = create(userEmail)
    serialize(cookieInfos).map { value =>
      Cookie(
        name = settings.cookieName,
        value = value,
        // The maxAge` must be used from the authenticator, because it might be changed by the user
        // to implement "Remember Me" functionality
        maxAge = cookieInfos.cookieMaxAge.map(_.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie,
        sameSite = settings.sameSite
      )
    }
  }

  def embed(cookie: Cookie, result: Result): Result =
    result.withCookies(cookie)

  def discard(result: Result): Result =
    result.discardingCookies(
      DiscardingCookie(
        name = settings.cookieName,
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie
      )
    )

}

object CookieAuthenticator {
  case class CookieAuthenticatorSettings(
      cookieName: String = "id",
      cookiePath: String = "/",
      cookieDomain: Option[String] = None,
      secureCookie: Boolean = true,
      httpOnlyCookie: Boolean = true,
      sameSite: Option[Cookie.SameSite] = Some(Cookie.SameSite.Lax),
      useFingerprinting: Boolean = true,
      cookieMaxAge: Option[FiniteDuration] = None,
      authenticatorIdleTimeout: Option[FiniteDuration] = None,
      authenticatorExpiry: FiniteDuration = 12.hours
  )
}
